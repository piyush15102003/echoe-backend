package com.echoe.backend.security;

import com.echoe.backend.service.MaintenanceMode;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs before every request. If maintenance mode is on, returns 503.
 * /health is always exempted so UptimeRobot keep-alive pings still work.
 */
@Component
@Order(1)
public class MaintenanceFilter implements Filter {

    private final MaintenanceMode maintenanceMode;

    public MaintenanceFilter(MaintenanceMode maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        // Always let health checks through
        if ("/health".equals(httpReq.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        if (maintenanceMode.isEnabled()) {
            HttpServletResponse httpRes = (HttpServletResponse) response;
            httpRes.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            httpRes.setContentType("application/json");
            httpRes.getWriter().write(
                "{\"error\":\"maintenance\",\"message\":\"Echoe is taking a short break. We'll be back soon.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }
}
