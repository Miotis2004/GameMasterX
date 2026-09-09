package com.gamemasterx.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class CsrfFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final String CSRF_SESSION_ATTR = "CSRF_TOKEN";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();

        // Ensure session and CSRF token exist
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(CSRF_SESSION_ATTR);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(CSRF_SESSION_ATTR, token);
        }

        if (SAFE_METHODS.contains(method)) {
            response.setHeader(CSRF_HEADER, token);
            filterChain.doFilter(request, response);
            return;
        }

        // State-changing methods require CSRF token validation
        String requestToken = request.getHeader(CSRF_HEADER);

        if (requestToken == null || !token.equals(requestToken)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
