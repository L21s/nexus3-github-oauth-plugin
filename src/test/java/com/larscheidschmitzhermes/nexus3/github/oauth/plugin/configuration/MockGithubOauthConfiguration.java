package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration;


import java.time.Duration;

public class MockGithubOauthConfiguration extends GithubOauthConfiguration {
    private Duration principalCacheTtl;

    private String org = "TEST-ORG";

    public MockGithubOauthConfiguration(Duration principalCacheTtl) {
        this.principalCacheTtl = principalCacheTtl;
    }

    @Override
    public String getGithubApiUrl() {
        return "http://github.example.com/api/v3";
    }

    @Override
    public Duration getPrincipalCacheTtl() {
        return principalCacheTtl;
    }

    @Override
    public String getGithubOrg() {
        return org;
    }

    public void setGithubOrg(String org) {
        this.org = org;
    }
}
