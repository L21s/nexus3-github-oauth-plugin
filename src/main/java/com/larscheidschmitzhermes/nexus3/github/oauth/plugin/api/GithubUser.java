package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubUser {
    private List<GithubTeam> teams;
    private String name;
    private String login;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    /**
     *
     * @return the real world name of the github user, null if not specified in github
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
