package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.GithubAuthenticationException;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.GithubPrincipal;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration.GithubOauthConfiguration;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@Named("GithubApiClient")
public class GithubApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GithubApiClient.class);

    HttpClient client;
    GithubOauthConfiguration configuration;
    ObjectMapper mapper;
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

    private GithubPrincipal doAuthz(String login, char[] token) throws GithubAuthenticationException {

        BasicHeader tokenHeader = new BasicHeader("Authorization", "token " + new String(token));
        HttpGet userRequest = new HttpGet(configuration.getGithubUserUri());
        userRequest.addHeader(tokenHeader);
        HttpResponse userResponse;

        try {
            userResponse = client.execute(userRequest);
        } catch (IOException e) {
            throw new GithubAuthenticationException(e);
        }

        if (userResponse.getStatusLine().getStatusCode() != 200) {
            LOGGER.warn("Authentication failed, status code was {}",
                    userResponse.getStatusLine().getStatusCode());
            userRequest.releaseConnection();
            throw new GithubAuthenticationException("Authentication failed.");
        }

        GithubUser githubUser;
        try {
            githubUser = mapper.readValue(new InputStreamReader(userResponse.getEntity().getContent()), GithubUser.class);
        } catch (IOException e) {
            throw new GithubAuthenticationException(e);
        }

        if (!login.equals(githubUser.getLogin())){
            throw new GithubAuthenticationException("Given username does not match Github Username!");
        }

        GithubPrincipal user = new GithubPrincipal();
        user.setUsername(githubUser.getName() != null ? githubUser.getName() : login);

        HttpGet orgsRequest = new HttpGet(configuration.getGithubOrgsUri());
        orgsRequest.addHeader(tokenHeader);
        HttpResponse orgsResponse;

        try {
            orgsResponse = client.execute(orgsRequest);
        } catch (IOException e) {
            orgsRequest.releaseConnection();
            throw new GithubAuthenticationException(e);
        }

        try {
            Set<GithubOrg> orgs = mapper.readValue(new InputStreamReader(orgsResponse.getEntity().getContent()), new TypeReference<Set<GithubOrg>>() {
            });
            Set<String> roles = orgs.stream().flatMap(org -> {
                HttpGet teamsRequest = null;
                try {
                    teamsRequest = new HttpGet(new URI(org.getUrl() + configuration.getGithubTeamsInOrgPath()));
                } catch (URISyntaxException e) {
                    LOGGER.warn("error creating uri" ,e);
                    throw new RuntimeException(e);
                }
                teamsRequest.addHeader(tokenHeader);
                HttpResponse teamsResponse;
                Set<GithubTeam> teams;
                try {
                    teamsResponse = client.execute(teamsRequest);
                    teams = mapper.readValue(new InputStreamReader(teamsResponse.getEntity().getContent()), new TypeReference<Set<GithubTeam>>() {
                    });
                } catch (IOException e) {
                    teamsRequest.releaseConnection();
                    LOGGER.warn("Failed to get teams",e);
                    teams = new HashSet<GithubTeam>();
                }
                return teams.stream().map(team -> {
                    return org.getLogin() + "/" + team.getName();
                }).collect(Collectors.toSet()).stream();
            }).collect(Collectors.toSet());
            user.setRoles(roles);
        } catch (IOException e) {
           throw new GithubAuthenticationException(e);
        }



        return user;
    }


}
