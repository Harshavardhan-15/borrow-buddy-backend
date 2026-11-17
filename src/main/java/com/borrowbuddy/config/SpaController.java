package com.borrowbuddy.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Arrays;

/**
 * Serves the React single-page application for client-side routes.
 *
 * <p>When the frontend build is packaged into {@code static/} (the monorepo
 * jar), these routes forward to {@code index.html} so React Router can render
 * the correct page on a hard refresh or a direct link.</p>
 *
 * <p>This API image is normally deployed on its own, with the React app hosted
 * separately (Vercel). In that case {@code static/index.html} is absent, so
 * opening a frontend path on the API origin would otherwise return a 404 JSON
 * body. Instead these routes redirect to the first configured frontend origin
 * ({@code app.cors.allowed-origins}, i.e. {@code FRONTEND_URL}) so a shared
 * link still lands on the app.</p>
 */
@Controller
public class SpaController {

    private final boolean bundled;
    private final String frontendUrl;

    public SpaController(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.bundled = new ClassPathResource("static/index.html").exists();
        this.frontendUrl = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .findFirst()
                .orElse("");
    }

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/browse",
            "/dashboard",
            "/requests",
            "/history",
            "/notifications",
            "/profile",
            "/reviews",
            "/items/new"
    })
    public Object index(HttpServletRequest request) {
        return frontendView(request.getRequestURI());
    }

    @GetMapping({
            "/items/{id:[0-9]+}",
            "/items/{id:[0-9]+}/edit",
            "/students/{id:[0-9]+}"
    })
    public Object indexWithId(@PathVariable Long id, HttpServletRequest request) {
        return frontendView(request.getRequestURI());
    }

    /**
     * Forwards to the packaged SPA when it is present, otherwise redirects the
     * same path to the configured frontend origin.
     */
    private Object frontendView(String path) {
        if (bundled) {
            return "forward:/index.html";
        }
        if (frontendUrl.isEmpty()) {
            return new RedirectView("/api/public/stats");
        }
        String suffix = path == null || "/".equals(path) ? "" : path;
        return new RedirectView(frontendUrl + suffix);
    }
}
