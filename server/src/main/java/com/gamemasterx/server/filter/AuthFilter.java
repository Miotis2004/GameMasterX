package com.gamemasterx.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

public class AuthFilter extends OncePerRequestFilter {

    public static final String AUTH_USER_ATTR = "authenticatedUserId";
    public static final String USER_ID_SESSION_ATTR = "userId";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        boolean isPublic = path.startsWith("/api/auth") || path.startsWith("/api/csrf");

        HttpSession session = request.getSession(false);
        String userId = null;
        if (session != null) {
            userId = (String) session.getAttribute(USER_ID_SESSION_ATTR);
        }

        // Establish authenticated user context
        request.setAttribute(AUTH_USER_ATTR, userId);
        if (userId != null) {
            MDC.put("userId", userId);
        }

        // Public endpoints bypass authentication requirement
        if (isPublic) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                if (userId != null) {
                    MDC.remove("userId");
                }
            }
            return;
        }

        // Safe methods do not require authentication, but context is established
        if (SAFE_METHODS.contains(method)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                if (userId != null) {
                    MDC.remove("userId");
                }
            }
            return;
        }

        // State-changing methods require authentication
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            String correlationId = (String) request.getAttribute("correlationId");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = "";
            }
            String timestamp = Instant.now().toString();
            String json = String.format("{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"correlationId\":\"%s\",\"timestamp\":\"%s\"}", correlationId, timestamp);
            response.getWriter().write(json);
            if (userId != null) {
                MDC.remove("userId");
            }
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
