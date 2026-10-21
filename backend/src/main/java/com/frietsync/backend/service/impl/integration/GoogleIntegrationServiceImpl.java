package com.frietsync.backend.service.impl.integration;

import com.frietsync.backend.config.integration.GoogleOAuthProperties;
import com.frietsync.backend.entity.integration.GoogleConnection;
import com.frietsync.backend.entity.integration.GoogleOAuthState;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.exception.ResourceNotFoundException;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;

@Service
@RequiredArgsConstructor
public class GoogleIntegrationServiceImpl implements GoogleIntegrationService {
    private static final String SCOPE = "openid email https://www.googleapis.com/auth/calendar.events";
    private final GoogleOAuthProperties properties;
    private final GoogleOAuthStateRepository stateRepository;
    private final GoogleConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final GoogleTokenCipher cipher;
    private final RestTemplate restTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public URI createAuthorizationUri(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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
                .queryParam("access_type", "offline")
                .queryParam("state", state).build().encode().toUri();
    }

    @Override
    @Transactional(noRollbackFor = InvalidOAuthStateException.class)
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
        if (tokens == null || !(tokens.get("access_token") instanceof String access)) {
            throw new OAuthExchangeException();
        }
        String refresh = tokens.get("refresh_token") instanceof String value ? value : null;
        GoogleConnection connection = connectionRepository.findByUser_Id(userId).orElseGet(GoogleConnection::new);
        if (refresh == null || refresh.isBlank()) {
            if (connection.getEncryptedRefreshToken() == null) throw new MissingRefreshTokenException();
        } else {
            connection.setEncryptedRefreshToken(cipher.encrypt(refresh));
        }
        Map<String, Object> profile = getGoogleProfile(access);
        connection.setUser(userRepository.getReferenceById(userId));
        connection.setGoogleSubject((String) profile.get("sub"));
        connection.setGoogleEmail((String) profile.get("email"));
        connection.setNeedsReauthorization(false);
        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void disconnect(UUID userId) {
        connectionRepository.findByUser_Id(userId).ifPresent(connection -> {
            revokeToken(cipher.decrypt(connection.getEncryptedRefreshToken()));
            connectionRepository.delete(connection);
        });
    }

    @Override
    @Transactional
    public String getValidAccessToken(UUID userId) {
        GoogleConnection connection = connectionRepository.findByUser_Id(userId)
                .orElseThrow(ReauthorizationRequiredException::new);
        if (connection.isNeedsReauthorization()) throw new ReauthorizationRequiredException();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("refresh_token", cipher.decrypt(connection.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");
        Map<String, Object> response = requestTokens(form);
        if (response == null || !(response.get("access_token") instanceof String access)) {
            throw new OAuthExchangeException();
        }
        return access;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConnected(UUID userId) {
        return connectionRepository.findByUser_Id(userId).isPresent();
    }

    @Override
    public GoogleIntegrationService.GoogleMeetEvent createMeetEvent(
            UUID userId, String title, String description,
            Instant startAt, Instant endAt, List<String> attendeeEmails) {
        String accessToken = getValidAccessToken(userId);
        List<Map<String, String>> attendees = new ArrayList<>();
        for (String email : attendeeEmails) {
            attendees.add(Map.of("email", email));
        }
        Map<String, Object> event = new HashMap<>();
        event.put("summary", title);
        event.put("description", description);
        event.put("start", Map.of("dateTime", DateTimeFormatter.ISO_INSTANT.format(startAt)));
        event.put("end", Map.of("dateTime", DateTimeFormatter.ISO_INSTANT.format(endAt)));
        event.put("attendees", attendees);
        event.put("conferenceData", Map.of("createRequest", Map.of(
                "requestId", UUID.randomUUID().toString(),
                "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        URI uri = UriComponentsBuilder.fromUriString(
                        "https://www.googleapis.com/calendar/v3/calendars/primary/events")
                .queryParam("conferenceDataVersion", 1)
                .queryParam("sendUpdates", "all")
                .build().encode().toUri();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.exchange(uri, HttpMethod.POST,
                new HttpEntity<>(event, headers), Map.class).getBody();
        String meetLink = response == null ? null : (String) response.get("hangoutLink");
        String eventId = response == null ? null : (String) response.get("id");
        if (meetLink == null && response != null && response.get("conferenceData") instanceof Map<?, ?> conference) {
            Object rawEntryPoints = conference.get("entryPoints");
            if (rawEntryPoints instanceof List<?> entryPoints) {
                for (Object entryPoint : entryPoints) {
                    if (entryPoint instanceof Map<?, ?> point && "video".equals(point.get("entryPointType"))
                            && point.get("uri") instanceof String uriValue) {
                        meetLink = uriValue;
                        break;
                    }
                }
            }
        }
        if (meetLink == null || meetLink.isBlank() || eventId == null || eventId.isBlank()) {
            if (eventId != null && !eventId.isBlank()) {
                deleteCalendarEvent(userId, eventId);
            }
            throw new OAuthExchangeException();
        }
        return new GoogleIntegrationService.GoogleMeetEvent(eventId, meetLink);
    }

    @Override
    public void deleteCalendarEvent(UUID userId, String calendarEventId) {
        String accessToken = getValidAccessToken(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        URI uri = UriComponentsBuilder.fromUriString(
                        "https://www.googleapis.com/calendar/v3/calendars/primary/events")
                .pathSegment(calendarEventId)
                .queryParam("sendUpdates", "all")
                .build().encode().toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        } catch (HttpClientErrorException.NotFound alreadyDeleted) { }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestTokens(LinkedMultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForObject("https://oauth2.googleapis.com/token",
                new HttpEntity<>(form, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getGoogleProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        Map<String, Object> profile = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
        if (profile == null || !(profile.get("sub") instanceof String)) throw new OAuthExchangeException();
        return profile;
    }

    private void revokeToken(String refreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        restTemplate.postForEntity("https://oauth2.googleapis.com/revoke",
                new HttpEntity<>(form, headers), Void.class);
    }

    private static String hash(String value) {
        return DigestUtils.sha256Hex(value);
    }
}
