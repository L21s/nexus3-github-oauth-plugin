package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.GithubAuthenticationException;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.GithubPrincipal;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration.GithubOauthConfiguration;

@Singleton
@Named("GithubApiClient")
public class GithubApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GithubApiClient.class);

    private HttpClient client;
    private GithubOauthConfiguration configuration;
    private ObjectMapper mapper;
    // Cache token lookups to reduce the load on Github's User API to prevent hitting the rate limit.
    private Cache<String, GithubPrincipal> tokenToPrincipalCache;

    public GithubApiClient() {
        init();
    }

    public GithubApiClient(HttpClient client, GithubOauthConfiguration configuration) {
        this.client = client;
        this.configuration = configuration;
        mapper = new ObjectMapper();
        initPrincipalCache();
    }

    @Inject
    public GithubApiClient(GithubOauthConfiguration configuration) {
        this.configuration = configuration;
        init();
    }

    public void init() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(configuration.getRequestConnectTimeout())
                .setConnectionRequestTimeout(configuration.getRequestConnectionRequestTimeout())
                .setSocketTimeout(configuration.getRequestSocketTimeout())
                .build();
        client = HttpClientBuilder
                .create()
                .setDefaultRequestConfig(config)
                .build();
        mapper = new ObjectMapper();
        initPrincipalCache();
    }

    private void initPrincipalCache() {
        tokenToPrincipalCache = CacheBuilder.newBuilder()
                .expireAfterWrite(configuration.getPrincipalCacheTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public GithubPrincipal authz(String login, char[] token) throws GithubAuthenticationException {
        // Combine the login and the token as the cache key since they are both used to generate the principal. If either changes we should obtain a new
        // principal.
        String cacheKey = login + "|" + new String(token);
        GithubPrincipal cached = tokenToPrincipalCache.getIfPresent(cacheKey);
        if (cached != null) {
            LOGGER.debug("Using cached principal for login: {}", login);
            return cached;
        } else {
            GithubPrincipal principal = doAuthz(login, token);
            tokenToPrincipalCache.put(cacheKey, principal);
            return principal;
        }
    }

    private GithubPrincipal doAuthz(String loginName, char[] token) throws GithubAuthenticationException {
        GithubUser githubUser = retrieveGithubUser(loginName, token);
        GithubPrincipal principal = new GithubPrincipal();

        principal.setUsername(githubUser.getLogin());
        principal.setRoles(generateRolesFromGithubOrgMemberships(token));

        return principal;
    }


    private GithubUser retrieveGithubUser(String loginName, char[] token) throws GithubAuthenticationException {
        GithubUser githubUser = getAndSerializeObject(configuration.getGithubUserUri(), token,GithubUser.class);

        if (!loginName.equals(githubUser.getLogin())) {
            throw new GithubAuthenticationException("Given username does not match Github Username!");
        }

        if (configuration.getGithubOrg() != null && !configuration.getGithubOrg().equals("")) {
            checkUserInOrg(configuration.getGithubOrg(), token);
        }
        return githubUser;
    }

    private void checkUserInOrg(String githubOrg, char[] token) throws GithubAuthenticationException {
        Set<GithubOrg> orgsInToken = getAndSerializeCollection(configuration.getGithubUserOrgsUri(), token, GithubOrg.class);
        String[] allowedOrgs = githubOrg.split(",");

        if (orgsInToken.stream().noneMatch(org -> Arrays.asList(allowedOrgs).contains(org.getLogin()))) {
            throw new GithubAuthenticationException("Given username is not in the Github Organization '" + githubOrg + "' or the Organization is not in the allowed list!");
        }
    }

    private Set<String> generateRolesFromGithubOrgMemberships(char[] token) throws GithubAuthenticationException {
        Set<GithubTeam> teams = getAndSerializeCollection(configuration.getGithubUserTeamsUri(), token, GithubTeam.class);
        return teams.stream().map(this::mapGithubTeamToNexusRole).collect(Collectors.toSet());
    }

    private String mapGithubTeamToNexusRole(GithubTeam team) {
        return team.getOrganization().getLogin() + "/" + team.getName();
    }

    private BasicHeader constructGithubAuthorizationHeader(char[] token) {
        return new BasicHeader("Authorization", "token " + new String(token));
    }

    private <T> T getAndSerializeObject(String uri, char[] token, Class<T> clazz) throws GithubAuthenticationException {
        try (InputStreamReader reader = executeGet(uri, token)) {
            JavaType javaType = mapper.getTypeFactory()
                    .constructType(clazz);
            return mapper.readValue(reader, javaType);
        } catch (IOException e) {
            throw new GithubAuthenticationException(e);
        }
    }

    private <T> Set<T> getAndSerializeCollection(String uri, char[] token, Class<T> clazz) throws GithubAuthenticationException {
        Set<T> result;
        try (InputStreamReader reader = executeGet(uri, token)) {
            JavaType javaType = mapper.getTypeFactory()
                    .constructCollectionType(Set.class, clazz);
            result = mapper.readValue(reader, javaType);
        } catch (IOException e) {
            throw new GithubAuthenticationException(e);
        }
        return result;
    }

    private InputStreamReader executeGet(String uri, char[] token) throws GithubAuthenticationException {
        HttpGet request = new HttpGet(uri);
        request.addHeader(constructGithubAuthorizationHeader(token));
        try {
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                LOGGER.warn("Authentication failed, status code was {}",
                        response.getStatusLine().getStatusCode());
                request.releaseConnection();
                throw new GithubAuthenticationException("Authentication failed.");
            }
            return new InputStreamReader(response.getEntity().getContent());
        } catch (IOException e) {
            request.releaseConnection();
            throw new GithubAuthenticationException(e);
        }

    }

}
