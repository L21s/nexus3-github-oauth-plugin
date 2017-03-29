package com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Singleton
@Named
public class GithubOauthConfiguration {
    private static final String CONFIG_FILE = "githuboauth.properties";

    private static final String GITHUB_USER_PATH = "/user";

    private static final String GITHUB_ORGS_PATH = "/user/orgs";

    private static final String GITHUB_TEAMS_IN_ORG_PATH = "/teams";

    private static final Logger LOGGER = LoggerFactory.getLogger(GithubOauthConfiguration.class);

    private Properties configuration;


    @PostConstruct
    public void init() {
        configuration = new Properties();
        try {
            configuration.load(Files.newInputStream(Paths.get(".", "etc", CONFIG_FILE)));
        } catch (IOException e) {
            LOGGER.error("Error reading github oauth properties", e);
        }
    }

    public String getGithubApiUrl() {
        return configuration.getProperty("github.api.url");

    }

    public String getGithubTeamsInOrgPath() {
        return GITHUB_TEAMS_IN_ORG_PATH;
    }

    public String getGithubOrgsUri() {
       return getGithubApiUrl() + GITHUB_ORGS_PATH;
    }

    public String getGithubUserUri() {
        return getGithubApiUrl() + GITHUB_USER_PATH;
    }
}
