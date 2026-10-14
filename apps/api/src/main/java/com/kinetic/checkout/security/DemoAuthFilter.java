package com.kinetic.checkout.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinetic.checkout.dto.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class DemoAuthFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final String demoToken;
    private final String testToken;

    public DemoAuthFilter(
            ObjectMapper json,
            @Value("${app.demo-token}") String demoToken,
            @Value("${app.test-token:}") String testToken) {
        this.json = json;
        this.demoToken = demoToken;
        this.testToken = testToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/")
                || path.equals("/api/products")
                || path.equals("/api/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String userId = ("Bearer " + demoToken).equals(authorization) ? "user_demo_1" : null;
        if (!testToken.isBlank() && ("Bearer " + testToken).equals(authorization)) {
            userId = "user_test_2";
        }
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.setHeader("WWW-Authenticate", "Bearer");
            json.writeValue(
                    response.getOutputStream(),
                    new ErrorResponse(
                            "UNAUTHORIZED", "A valid demo bearer token is required.", Map.of()));
            return;
        }
        request.setAttribute("userId", userId);
        chain.doFilter(request, response);
    }
}
