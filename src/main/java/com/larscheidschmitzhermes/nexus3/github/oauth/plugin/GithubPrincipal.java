package com.larscheidschmitzhermes.nexus3.github.oauth.plugin;

import java.util.Set;

public class GithubPrincipal {
    private String username;
    private char[] oauthToken;
    private Set<String> roles;

    public GithubPrincipal(){

    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setOauthToken(char[] oauthToken) {
        this.oauthToken = oauthToken;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public char[] getOauthToken() {
        return oauthToken;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return username;
    }
}
