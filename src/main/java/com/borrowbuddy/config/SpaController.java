package com.borrowbuddy.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React single-page application for client-side routes when the
 * build is packaged into {@code static/}.
 *
 * <p>This API image is deployed on its own and the React app is hosted on
 * Vercel, so {@code static/} is normally empty and these routes are not used.
 * If you package the frontend into the jar (copy {@code dist/} into
 * {@code src/main/resources/static/}), static assets are served directly and
 * the routes below forward to {@code index.html} so React Router can render the
 * correct page on a hard refresh or a direct link.</p>
 */
@Controller
public class SpaController {

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
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping({
            "/items/{id:[0-9]+}",
            "/items/{id:[0-9]+}/edit",
            "/students/{id:[0-9]+}"
    })
    public String indexWithId() {
        return "forward:/index.html";
    }
}
