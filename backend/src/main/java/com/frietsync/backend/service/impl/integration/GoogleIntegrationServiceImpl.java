package com.frietsync.backend.service.impl.integration;

import com.frietsync.backend.config.integration.GoogleOAuthProperties;
import com.frietsync.backend.entity.integration.GoogleConnection;
import com.frietsync.backend.entity.integration.GoogleOAuthState;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.repository.integration.GoogleConnectionRepository;
import com.frietsync.backend.repository.integration.GoogleOAuthStateRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.integration.GoogleIntegrationService;
import com.frietsync.backend.service.integration.GoogleTokenCipher;
import com.frietsync.backend.service.integration.InvalidOAuthStateException;
import com.frietsync.backend.service.integration.MissingRefreshTokenException;
import com.frietsync.backend.service.integration.OAuthExchangeException;
import com.frietsync.backend.service.integration.ReauthorizationRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleIntegrationServiceImpl implements GoogleIntegrationService {
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private final GoogleOAuthProperties properties;
    private final GoogleOAuthStateRepository stateRepository;
    private final GoogleConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final GoogleTokenCipher cipher;
    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public URI createAuthorizationUri(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        GoogleOAuthState pending = new GoogleOAuthState();
        pending.setUser(user);
        pending.setStateHash(hash(state));
        pending.setExpiresAt(Instant.now().plusSeconds(properties.getStateTtlSeconds()));
        stateRepository.save(pending);
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code").queryParam("scope", SCOPE)
                .queryParam("access_type", "offline").queryParam("prompt", "consent")
                .queryParam("state", state).build().encode().toUri();
    }

    @Override
    @Transactional
    public UUID consumeState(String state) {
        if (state == null || state.length() > 200) throw new InvalidOAuthStateException();
        GoogleOAuthState pending = stateRepository.findByHashForUpdate(hash(state))
                .orElseThrow(InvalidOAuthStateException::new);
        stateRepository.delete(pending);
        if (pending.getExpiresAt().isBefore(Instant.now())) throw new InvalidOAuthStateException();
        return pending.getUser().getId();
    }

    @Override
    @Transactional
    public void complete(UUID userId, String code) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", "authorization_code");
        Map<String, Object> tokens = requestTokens(form);
        if (tokens == null || !(tokens.get("access_token") instanceof String access)) throw new OAuthExchangeException();
        String refresh = tokens.get("refresh_token") instanceof String value ? value : null;
        GoogleConnection connection = connectionRepository.findByUser_Id(userId).orElseGet(GoogleConnection::new);
        if (refresh == null || refresh.isBlank()) {
            if (connection.getEncryptedRefreshToken() == null) throw new MissingRefreshTokenException();
        } else {
            connection.setEncryptedRefreshToken(cipher.encrypt(refresh));
        }
        connection.setUser(userRepository.getReferenceById(userId));
        connection.setNeedsReauthorization(false);
        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUser_Id(userId).ifPresent(connectionRepository::delete);
    }

    @Override
    @Transactional(noRollbackFor = ReauthorizationRequiredException.class)
    public String getValidAccessToken(UUID userId) {
        GoogleConnection connection = connectionRepository.findByUser_Id(userId)
                .orElseThrow(ReauthorizationRequiredException::new);
        if (connection.isNeedsReauthorization()) throw new ReauthorizationRequiredException();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("refresh_token", cipher.decrypt(connection.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");
        try {
            Map<String, Object> response = requestTokens(form);
            if (response == null || !(response.get("access_token") instanceof String access)) throw new OAuthExchangeException();
            return access;
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
                connection.setNeedsReauthorization(true);
                throw new ReauthorizationRequiredException();
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestTokens(LinkedMultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForObject("https://oauth2.googleapis.com/token",
                new HttpEntity<>(form, headers), Map.class);
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
