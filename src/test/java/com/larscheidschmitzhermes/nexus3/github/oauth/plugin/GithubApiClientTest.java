package com.larscheidschmitzhermes.nexus3.github.oauth.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api.GithubApiClient;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api.GithubOrg;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api.GithubTeam;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.api.GithubUser;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration.GithubOauthConfiguration;
import com.larscheidschmitzhermes.nexus3.github.oauth.plugin.configuration.MockGithubOauthConfiguration;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GithubApiClientTest {
    private MockGithubOauthConfiguration config = new MockGithubOauthConfiguration(Duration.ofDays(1));
    private ObjectMapper mapper = new ObjectMapper();

    private List<GithubTeam> mockTeams() {
        GithubOrg org = new GithubOrg();
        org.setLogin("TEST-ORG");

        List<GithubTeam> teams = new ArrayList<>();

        GithubTeam team = new GithubTeam();
        team.setOrganization(org);
        team.setName("admin");
        teams.add(team);

        return teams;
    }

    private List<GithubTeam> mockTooManyTeams() {
        List<GithubTeam> teams = new ArrayList<>();

        GithubOrg org = new GithubOrg();
        org.setLogin("TEST-ORG");
        for (int i = 0; i < 100; i++) {
            GithubTeam team = new GithubTeam();
            team.setOrganization(org);
            team.setName("admin_team_"+i);
            teams.add(team);
        }

        return teams;
    }

    private GithubUser mockUser(String username) {
        GithubUser user = new GithubUser();
        user.setName(username);
        user.setLogin("demo-user");
        return user;
    }

    private Set<GithubOrg> mockOrg(String orgname) {
        Set orgs = new HashSet();
        GithubOrg org = new GithubOrg();
        org.setLogin(orgname);
        orgs.add(org);
        return orgs;
    }

    private HttpResponse createMockResponse(Object entity) throws IOException {
        HttpResponse mockOrgResponse = mock(HttpResponse.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockOrgResponse.getStatusLine().getStatusCode()).thenReturn(200);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mapper.writeValue(baos, entity);
        byte[] data = baos.toByteArray();
        when(mockOrgResponse.getEntity().getContent()).thenReturn(new ByteArrayInputStream(data));
        return mockOrgResponse;
    }

    private HttpClient fullyFunctionalMockClient() throws IOException {
        HttpClient mockClient = mock(HttpClient.class);
        mockResponsesForGithubAuthRequest(mockClient);
        return mockClient;
    }

    private HttpClient fullyFunctionalMockClient2ManyTeams() throws IOException {
        HttpClient mockClient = mock(HttpClient.class);
        mockResponsesForGithubAuthRequest2Many(mockClient);
        return mockClient;
    }

    private void mockResponsesForGithubAuthRequest(HttpClient mockClient) throws IOException {
        HttpResponse mockUserResponse = createMockResponse(mockUser("Hans Wurst"));
        when(mockClient.execute(Mockito.any())).thenAnswer(invocationOnMock -> answerOnInvocation(invocationOnMock, mockUserResponse));
    }

    private void mockResponsesForGithubAuthRequest2Many(HttpClient mockClient) throws IOException {
        HttpResponse mockUserResponse = createMockResponse(mockUser("Hans Wurst"));
        when(mockClient.execute(Mockito.any())).thenAnswer(invocationOnMock -> answerOnInvocation2Many(invocationOnMock, mockUserResponse));
    }

    private HttpResponse answerOnInvocation(InvocationOnMock invocationOnMock, HttpResponse mockUserResponse) throws IOException {
        HttpResponse mockTeamResponse = createMockResponse(mockTeams());
        HttpResponse mockOrgsResponse = createMockResponse(mockOrg("TEST-ORG"));

        String uriString = ((HttpGet) invocationOnMock.getArguments()[0]).getURI().toString();
        if (uriString.equals(config.getGithubUserTeamsUri())) {
            return mockTeamResponse;
        } else if (uriString.equals(config.getGithubUserUri())) {
            return mockUserResponse;
        } else if (uriString.equals(config.getGithubUserOrgsUri())) {
            return mockOrgsResponse;
        }
        return null;
    }

    private HttpResponse answerOnInvocation2Many(InvocationOnMock invocationOnMock, HttpResponse mockUserResponse) throws IOException {
        HttpResponse mockTeamResponse = createMockResponse(mockTooManyTeams());
        HttpResponse mockOrgsResponse = createMockResponse(mockOrg("TEST-ORG"));

        String uriString = ((HttpGet) invocationOnMock.getArguments()[0]).getURI().toString();
        if (uriString.equals(config.getGithubUserTeamsUri())) {
            return mockTeamResponse;
        } else if (uriString.equals(config.getGithubUserUri())) {
            return mockUserResponse;
        } else if (uriString.equals(config.getGithubUserOrgsUri())) {
            return mockOrgsResponse;
        }
        return null;
    }

    @Test
    public void shouldWarnIfTooManyTeams() throws Exception {
        mockStatic(LoggerFactory.class);
        Logger logger = mock(Logger.class);
        when(LoggerFactory.getLogger(any(Class.class))).thenReturn(logger);

        HttpClient mockClient = fullyFunctionalMockClient2ManyTeams();

        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        GithubPrincipal authorizedPrincipal = clientToTest.authz("demo-user", "DUMMY".toCharArray());

        verify(logger).warn("Fetching only the first 100 teams for user '{}'","demo-user");

        MatcherAssert.assertThat(authorizedPrincipal.getRoles().size(), Is.is(100));
        MatcherAssert.assertThat(authorizedPrincipal.getUsername(), Is.is("demo-user"));
    }

    @Test
    public void shouldDoAuthzIfRequestStatusIs200() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();

        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        GithubPrincipal authorizedPrincipal = clientToTest.authz("demo-user", "DUMMY".toCharArray());

        MatcherAssert.assertThat(authorizedPrincipal.getRoles().size(), Is.is(1));
        MatcherAssert.assertThat(authorizedPrincipal.getRoles().iterator().next(), Is.is("TEST-ORG/admin"));
        MatcherAssert.assertThat(authorizedPrincipal.getUsername(), Is.is("demo-user"));
    }

    @Test(expected = GithubAuthenticationException.class)
    public void shouldNotAuthenticateIfRequestIsNot200() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse mockResponse = mock(HttpResponse.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockResponse.getStatusLine().getStatusCode()).thenReturn(403);
        when(mockClient.execute(Mockito.any())).thenReturn(mockResponse);

        GithubApiClient clientToTest = new GithubApiClient(mockClient, new MockGithubOauthConfiguration(Duration.ofDays(1)));

        clientToTest.authz("demo-user", "DUMMY".toCharArray());
    }

    @Test(expected = GithubAuthenticationException.class)
    public void shouldNotAuthenticateIfUsernameDoesntMatch() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();

        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        clientToTest.authz("not-the-demo-user", "DUMMY".toCharArray());
    }

    @Test(expected = GithubAuthenticationException.class)
    public void shouldNotAuthenticateIfUserNotInOrg() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();
        config.setGithubOrg("OTHER-ORG");
        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        clientToTest.authz("demo-user", "DUMMY".toCharArray());
    }

    @Test
    public void cachedPrincipalReturnsIfNotExpired() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();

        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        String login = "demo-user";
        char[] token = "DUMMY".toCharArray();
        clientToTest.authz(login, token);

        // We make 2 calls to Github for a single auth check
        Mockito.verify(mockClient, Mockito.times(3)).execute(Mockito.any(HttpGet.class));
        Mockito.verifyNoMoreInteractions(mockClient);

        // This invocation should hit the cache and should not use the client
        clientToTest.authz(login, token);
        Mockito.verifyNoMoreInteractions(mockClient);
    }

    @Test
    public void shouldNotCheckOrgDuringAuthentication() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();
        config.setGithubOrg(null);

        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        String login = "demo-user";
        char[] token = "DUMMY".toCharArray();
        clientToTest.authz(login, token);

        // We make 2 calls to Github for a single auth check
        Mockito.verify(mockClient, Mockito.times(2)).execute(Mockito.any(HttpGet.class));
        Mockito.verifyNoMoreInteractions(mockClient);
    }

    @Test
    public void principalCacheHonorsTtl() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();

        GithubOauthConfiguration configWithShortCacheTtl = new MockGithubOauthConfiguration(Duration.ofMillis(1));
        GithubApiClient clientToTest = new GithubApiClient(mockClient, configWithShortCacheTtl);
        char[] token = "DUMMY".toCharArray();

        clientToTest.authz("demo-user", token);
        // We make 2 calls to Github for a single auth check
        Mockito.verify(mockClient, Mockito.times(3)).execute(Mockito.any(HttpGet.class));
        Mockito.verifyNoMoreInteractions(mockClient);

        // Wait a bit for the cache to become invalidated
        Thread.sleep(10);

        // Mock the responses again so a second auth attempt works
        Mockito.reset(mockClient);
        mockResponsesForGithubAuthRequest(mockClient);
        // This should also hit Github because the cache TTL has elapsed
        clientToTest.authz("demo-user", token);
        // We make 3 calls to Github for a single auth check
        Mockito.verify(mockClient, Mockito.times(3)).execute(Mockito.any(HttpGet.class));
        Mockito.verifyNoMoreInteractions(mockClient);
    }

    @Test
    public void shouldAcceptOrgAnywhereInList() throws Exception {
        HttpClient mockClient = fullyFunctionalMockClient();
        config.setGithubOrg("TEST-ORG,TEST-ORG2");
        GithubApiClient clientToTest = new GithubApiClient(mockClient, config);
        GithubPrincipal authorizedPrincipal = clientToTest.authz("demo-user", "DUMMY".toCharArray());
        MatcherAssert.assertThat(authorizedPrincipal.getRoles().iterator().next(), Is.is("TEST-ORG/admin"));

        HttpClient mockClient2 = fullyFunctionalMockClient();
        config.setGithubOrg("TEST-ORG2,TEST-ORG");
        GithubApiClient clientToTest2 = new GithubApiClient(mockClient2, config);
        GithubPrincipal authorizedPrincipal2 = clientToTest2.authz("demo-user", "DUMMY".toCharArray());
        MatcherAssert.assertThat(authorizedPrincipal2.getRoles().iterator().next(), Is.is("TEST-ORG/admin"));
    }
}
