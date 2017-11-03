package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubTeam {
    private String name;

    private GithubOrg organization;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GithubOrg getOrganization() {
        return organization;
    }

    public void setOrganization(GithubOrg organization) {
        this.organization = organization;
    }
}
