package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerLoginRefreshAndLogoutFlow() throws Exception {
        String username = uniqueUsername();
        TokenPair registered = register(username);
        assertThat(registered.accessToken()).isNotBlank();
        assertThat(registered.refreshToken()).isNotBlank();

        JsonNode me = body(getJson("/api/v1/users/me", registered.accessToken()));
        assertThat(me.path("code").asInt()).isZero();
        assertThat(me.path("data").path("username").asText()).isEqualTo(username);
        assertThat(me.path("data").path("roles").toString()).contains("CUSTOMER");

        TokenPair loggedIn = login(username, TEST_PASSWORD);
        TokenPair refreshed = refreshToken(loggedIn.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();

        MvcResult reusedRefresh = postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", loggedIn.refreshToken()));
        assertThat(reusedRefresh.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(reusedRefresh).path("code").asInt()).isEqualTo(11008);

        MvcResult logout = postJson("/api/v1/auth/logout",
                Map.of("refreshToken", refreshed.refreshToken()),
                refreshed.accessToken());
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);

        MvcResult afterLogout = getJson("/api/v1/users/me", refreshed.accessToken());
        assertThat(afterLogout.getResponse().getStatus()).isEqualTo(401);

        MvcResult refreshAfterLogout = postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshed.refreshToken()));
        assertThat(refreshAfterLogout.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void loginLocksAfterRepeatedFailures() throws Exception {
        String username = uniqueUsername();
        register(username);

        for (int i = 0; i < 5; i++) {
            MvcResult failed = postJson("/api/v1/auth/login",
                    Map.of("username", username, "password", "Wrong@123456"));
            assertThat(failed.getResponse().getStatus()).isEqualTo(401);
            assertThat(body(failed).path("code").asInt()).isEqualTo(11006);
        }

        MvcResult locked = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", TEST_PASSWORD));
        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(locked).path("code").asInt()).isEqualTo(11007);
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = uniqueUsername();
        register(username);

        MvcResult duplicate = postJson("/api/v1/auth/register",
                Map.of("username", username, "password", TEST_PASSWORD));
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate).path("code").asInt()).isEqualTo(11001);
    }

    @Test
    void protectedEndpointRequiresValidAccessToken() throws Exception {
        MvcResult anonymous = getJson("/api/v1/users/me", null);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(anonymous).path("code").asInt()).isEqualTo(10001);

        MvcResult invalid = getJson("/api/v1/users/me", "not-a-jwt");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(401);
    }
}
