package com.frietsync.backend.config.ratelimit;

import com.frietsync.backend.service.impl.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }

        String path = request.getRequestURI();
        String ip = getClientIp(request);

        int ipLimit = getIpLimit(path);

        String ipKey = "rate:ip:" + ip + ":" + path;

        if (!rateLimitService.isAllowed(
                ipKey,
                ipLimit,
                Duration.ofMinutes(1)
        )) {
            return rateLimitExceeded(response);
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

            String userId = authentication.getName();
            int userLimit = getUserLimit(path);
            String userKey = "rate:user:" + userId + ":" + path;

            if (!rateLimitService.isAllowed(
                    userKey,
                    userLimit,
                    Duration.ofMinutes(1)
            )) {
                return rateLimitExceeded(response);
            }
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {

        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isBlank()) {
            return request.getRemoteAddr();
        }

        return ip.split(",")[0].trim();
    }

    private int getIpLimit(String path) {

        if (path.equals("/api/v1/auth/login")) {
            return 10;
        }

        if (path.equals("/api/v1/auth/signup")
                || path.equals("/api/v1/auth/forgot-password")) {
            return 10;
        }

        if (path.equals("/api/v1/auth/refresh")) {
            return 5;
        }

        if (path.equals("/api/v1/auth/logout")) {
            return 10;
        }

        return 100;
    }

    private int getUserLimit(String path) {

        if (path.equals("/api/v1/auth/refresh")) {
            return 5;
        }

        if (path.equals("/api/v1/auth/logout")) {
            return 10;
        }

        return 100;
    }

    private boolean rateLimitExceeded(HttpServletResponse response)
            throws Exception {

        response.setStatus(429);
        response.setContentType("application/json");

        response.getWriter().write(
                "{\"status\":429,\"message\":\"Too many requests, try again in a minute\"}"
        );

        return false;
    }
}