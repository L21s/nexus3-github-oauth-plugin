package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration;


public class MockGithubOauthConfiguration extends GithubOauthConfiguration {
    @Override
    public String getGithubApiUrl() {
        return "http://github.example.com/api/v3";
    }

}
