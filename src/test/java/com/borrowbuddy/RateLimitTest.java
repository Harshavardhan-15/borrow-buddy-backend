package com.borrowbuddy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies the auth rate limiter returns 429 with a Retry-After header once a
 * client exceeds the configured number of attempts, and that unrelated
 * endpoints are unaffected.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.auth-per-minute=3"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {

    private static final String BAD_LOGIN =
            "{\"collegeEmail\":\"nobody@college.edu\",\"password\":\"wrong-password\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksLoginAfterTooManyAttempts() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            MvcResult allowed = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BAD_LOGIN)).andReturn();
            assertNotEquals(429, allowed.getResponse().getStatus(),
                    "attempt " + attempt + " should not be rate limited");
        }

        MvcResult blocked = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BAD_LOGIN)).andReturn();

        assertEquals(429, blocked.getResponse().getStatus());
        String retryAfter = blocked.getResponse().getHeader("Retry-After");
        assertNotNull(retryAfter);
        assertTrue(Long.parseLong(retryAfter) >= 1, retryAfter);
        assertTrue(blocked.getResponse().getContentAsString().contains("\"success\":false"));
    }

    @Test
    void blocksRegistrationAfterTooManyAttempts() throws Exception {
        String body = "{\"fullName\":\"Spam Bot\",\"collegeEmail\":\"spam@college.edu\","
                + "\"studentId\":\"STU9999999\",\"password\":\"Password123\"}";

        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        MvcResult blocked = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();

        assertEquals(429, blocked.getResponse().getStatus());
    }

    @Test
    void leavesPublicEndpointsAlone() throws Exception {
        for (int i = 0; i < 10; i++) {
            MvcResult response = mockMvc.perform(get("/api/public/stats")).andReturn();
            assertEquals(200, response.getResponse().getStatus());
        }
    }
}
