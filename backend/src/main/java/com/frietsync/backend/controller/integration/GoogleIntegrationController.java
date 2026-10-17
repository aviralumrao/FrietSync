package com.frietsync.backend.controller.integration;

import com.frietsync.backend.config.integration.GoogleOAuthProperties;
import com.frietsync.backend.service.integration.GoogleIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/google")
@RequiredArgsConstructor
public class GoogleIntegrationController {
    private final GoogleIntegrationService service;
    private final GoogleOAuthProperties properties;

    @GetMapping("/connect")
    public ResponseEntity<?> connect(@AuthenticationPrincipal UUID userId,
                                     @RequestHeader(value = "Accept", required = false) String accept) {
        URI authorizationUri = service.createAuthorizationUri(userId);
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return ResponseEntity.ok(Map.of("authorizationUrl", authorizationUri.toString()));
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(String state, String code, String error) {
        UUID userId = service.consumeState(state);
        if (error != null) return redirect("denied");
        if (code == null || code.isBlank()) return redirect("failed");
        service.complete(userId, code);
        return redirect("connected");
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UUID userId) {
        service.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> redirect(String result) {
        URI target = UriComponentsBuilder.fromUriString(properties.getFrontendResultUri())
                .queryParam("google", result).build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
