package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
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
        //no args constructor is needed
    }

    public GithubApiClient(HttpClient client, GithubOauthConfiguration configuration) {
        this.client = client;
        this.configuration = configuration;
        mapper = new ObjectMapper();
        initPrincipalCache();
    }

    @PostConstruct
    public void init() {
        client = HttpClientBuilder.create().build();
        mapper = new ObjectMapper();
        initPrincipalCache();
    }

    private void initPrincipalCache() {
        tokenToPrincipalCache = CacheBuilder.newBuilder()
                .expireAfterWrite(configuration.getPrincipalCacheTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Inject
    public GithubApiClient(GithubOauthConfiguration configuration) {
        this.configuration = configuration;
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

        principal.setUsername(githubUser.getName() != null ? githubUser.getName() : loginName);
        principal.setRoles(generateRolesFromGithubOrgMemberships(githubUser, token));

        return principal;
    }

    private GithubUser retrieveGithubUser(String loginName, char[] token) throws GithubAuthenticationException {
        try {
            HttpGet userRequest = new HttpGet(configuration.getGithubUserUri());
            userRequest.addHeader(constructGithubAuthorizationHeader(token));
            HttpResponse userResponse = client.execute(userRequest);

            if (userResponse.getStatusLine().getStatusCode() != 200) {
                LOGGER.warn("Authentication failed, status code was {}",
                        userResponse.getStatusLine().getStatusCode());
                userRequest.releaseConnection();
                throw new GithubAuthenticationException("Authentication failed.");
            }

            GithubUser githubUser = mapper.readValue(new InputStreamReader(userResponse.getEntity().getContent()), GithubUser.class);

            if (!loginName.equals(githubUser.getLogin())){
                throw new GithubAuthenticationException("Given username does not match Github Username!");
            }

            return githubUser;
        } catch (IOException e) {
            throw new GithubAuthenticationException(e);
        }
    }

    private Set<String> generateRolesFromGithubOrgMemberships(GithubUser githubUser, char[] token) throws GithubAuthenticationException{
        HttpGet orgsRequest = new HttpGet(configuration.getGithubOrgsUri());
        orgsRequest.addHeader(constructGithubAuthorizationHeader(token));
        HttpResponse orgsResponse;

        Set<GithubOrg> orgs;
        try {
            orgsResponse = client.execute(orgsRequest);
             orgs = mapper.readValue(new InputStreamReader(orgsResponse.getEntity().getContent()), new TypeReference<Set<GithubOrg>>() {
            });
        } catch (IOException e) {
            orgsRequest.releaseConnection();
            throw new GithubAuthenticationException(e);
        }

        Set<String> roles = new HashSet<>();

        for (GithubOrg org : orgs) {
            roles.addAll(retrieveTeamMembershipsInOrg(org, token).stream().map(team -> mapGithubTeamToNexusRole(org, team)).collect(Collectors.toList()));
        }

        return roles;
    }

    private Set<GithubTeam> retrieveTeamMembershipsInOrg(GithubOrg org, char[] token) {
        HttpGet teamsRequest;
        try {
            teamsRequest = new HttpGet(new URI(org.getUrl() + configuration.getGithubTeamsInOrgPath()));
        } catch (URISyntaxException e) {
            LOGGER.warn("error creating uri" ,e);
            return Collections.emptySet();
        }
        teamsRequest.addHeader(constructGithubAuthorizationHeader(token));
        try {
            HttpResponse teamsResponse = client.execute(teamsRequest);
            return mapper.readValue(new InputStreamReader(teamsResponse.getEntity().getContent()), new TypeReference<Set<GithubTeam>>() {
            });
        } catch (IOException e) {
            teamsRequest.releaseConnection();
            LOGGER.warn("Failed to get teams in " + org.getUrl(),e);
            return Collections.emptySet();
        }
    }

    private String mapGithubTeamToNexusRole(GithubOrg org, GithubTeam team) {
        return org.getLogin() + "/" + team.getName();
    }

    private BasicHeader constructGithubAuthorizationHeader(char[] token) {
        return new BasicHeader("Authorization", "token " + new String(token));
    }

}
