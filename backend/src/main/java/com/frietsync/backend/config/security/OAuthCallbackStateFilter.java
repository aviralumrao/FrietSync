package com.frietsync.backend.config.security;

import com.frietsync.backend.repository.integration.GitHubOAuthStateRepository;
import com.frietsync.backend.repository.integration.GoogleOAuthStateRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuthCallbackStateFilter extends OncePerRequestFilter {
    private static final String GOOGLE_CALLBACK = "/api/v1/integrations/google/callback";
    private static final String GITHUB_CALLBACK = "/api/v1/integrations/github/callback";
    private final GoogleOAuthStateRepository googleStates;
    private final GitHubOAuthStateRepository githubStates;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("GET".equals(request.getMethod()) && (GOOGLE_CALLBACK.equals(path) || GITHUB_CALLBACK.equals(path))) {
            String state = request.getParameter("state");
            boolean valid = state != null && state.length() <= 200
                    && (GOOGLE_CALLBACK.equals(path)
                    ? googleStates.existsByStateHashAndExpiresAtAfter(DigestUtils.sha256Hex(state), Instant.now())
                    : githubStates.existsByStateHashAndExpiresAtAfter(DigestUtils.sha256Hex(state), Instant.now()));
            if (!valid) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired OAuth state");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "oauth-callback", null, List.of(new SimpleGrantedAuthority("ROLE_OAUTH_CALLBACK"))));
        }
        chain.doFilter(request, response);
    }
}
