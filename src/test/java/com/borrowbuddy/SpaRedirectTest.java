package com.borrowbuddy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The API-only image does not bundle {@code static/index.html}, so frontend
 * routes opened on the API origin must redirect to the configured frontend
 * origin instead of returning a 404 JSON body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaRedirectTest {

    private static final String FRONTEND = "http://localhost:8080";

    /** True when the build packaged {@code frontend-react/dist} into the jar. */
    private static final boolean BUNDLED = new ClassPathResource("static/index.html").exists();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootRedirectsToTheFrontendOrigin() throws Exception {
        assumeFalse(BUNDLED, "the packaged jar serves the SPA instead of redirecting");

        MvcResult response = mockMvc.perform(get("/")).andReturn();

        assertEquals(302, response.getResponse().getStatus());
        assertEquals(FRONTEND, response.getResponse().getRedirectedUrl());
    }

    @Test
    void frontendRoutesKeepTheirPathWhenRedirected() throws Exception {
        assumeFalse(BUNDLED, "the packaged jar serves the SPA instead of redirecting");

        assertEquals(FRONTEND + "/dashboard", redirectFor("/dashboard"));
        assertEquals(FRONTEND + "/items/13", redirectFor("/items/13"));
        assertEquals(FRONTEND + "/items/13/edit", redirectFor("/items/13/edit"));
        assertEquals(FRONTEND + "/students/7", redirectFor("/students/7"));
    }

    @Test
    void unknownPublicApiPathsStillReturnTheJsonNotFoundEnvelope() throws Exception {
        MvcResult response = mockMvc.perform(get("/api/public/does-not-exist")).andReturn();

        assertEquals(404, response.getResponse().getStatus());
        assertTrue(response.getResponse().getContentAsString().contains("\"message\":\"Resource not found\""));
    }

    private String redirectFor(String path) throws Exception {
        MvcResult response = mockMvc.perform(get(path)).andReturn();
        assertEquals(302, response.getResponse().getStatus());
        return response.getResponse().getRedirectedUrl();
    }
}
