package com.larscheidschmitzhermes.nexus3.github.oauth.plugin;

public class GithubAuthenticationException extends Exception{
    public GithubAuthenticationException(String message){
        super(message);
    }

    public GithubAuthenticationException(Throwable cause){
        super(cause);
    }

}
