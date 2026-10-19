package com.frietsync.backend.service.impl.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.frietsync.backend.config.call.JanusConfig;
import com.frietsync.backend.service.call.JanusService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class JanusServiceImpl implements JanusService {

    private final JanusConfig janusConfig;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(janusConfig.baseUrl())
                .build();
    }

    @Override
    public long createRoom() {
        long sessionId = createSession();
        long handleId = 0;

        try {
            handleId = attachAudioBridge(sessionId);

            long roomId = ThreadLocalRandom.current()
                    .nextLong(100_000_000L, 9_000_000_000L);

            Map<String, Object> room = new HashMap<>();
            room.put("request", "create");
            room.put("room", roomId);
            room.put("description", "FrietSync voice room");
            room.put("sampling_rate", 16000);
            room.put("record", false);

            if (hasText(janusConfig.roomSecret())) {
                room.put("secret", janusConfig.roomSecret());
            }

            if (hasText(janusConfig.roomPin())) {
                room.put("pin", janusConfig.roomPin());
            }

            JsonNode response = post(
                    "/%d/%d".formatted(sessionId, handleId),
                    Map.of(
                            "janus", "message",
                            "transaction", transaction(),
                            "body", room
                    )
            );

            JsonNode data = response.path("plugindata").path("data");

            if (!"created".equals(data.path("audiobridge").asText())
                    || data.path("room").asLong() != roomId) {
                throw new IllegalStateException(
                        "Janus did not confirm room creation: " + response
                );
            }

            return roomId;

        } finally {
            destroySession(sessionId);
        }
    }

    @Override
    public void destroyRoom(long roomId) {
        long sessionId = createSession();

        try {
            long handleId = attachAudioBridge(sessionId);

            Map<String, Object> body = new HashMap<>();
            body.put("request", "destroy");
            body.put("room", roomId);

            if (hasText(janusConfig.roomSecret())) {
                body.put("secret", janusConfig.roomSecret());
            }

            JsonNode response = post(
                    "/%d/%d".formatted(sessionId, handleId),
                    Map.of(
                            "janus", "message",
                            "transaction", transaction(),
                            "body", body
                    )
            );

            JsonNode data = response.path("plugindata").path("data");
            String result = data.path("audiobridge").asText();

            if (!"destroyed".equals(result)) {
                throw new IllegalStateException(
                        "Janus did not confirm room destruction: " + response
                );
            }

        } finally {
            destroySession(sessionId);
        }
    }

    private long createSession() {
        JsonNode response = post(
                "",
                Map.of(
                        "janus", "create",
                        "transaction", transaction()
                )
        );

        requireSuccess(response);

        long sessionId = response.path("data").path("id").asLong();

        if (sessionId <= 0) {
            throw new IllegalStateException(
                    "Janus returned an invalid session ID"
            );
        }

        return sessionId;
    }

    private long attachAudioBridge(long sessionId) {
        JsonNode response = post(
                "/" + sessionId,
                Map.of(
                        "janus", "attach",
                        "plugin", "janus.plugin.audiobridge",
                        "transaction", transaction()
                )
        );

        requireSuccess(response);

        long handleId = response.path("data").path("id").asLong();

        if (handleId <= 0) {
            throw new IllegalStateException(
                    "Janus returned an invalid plugin handle ID"
            );
        }

        return handleId;
    }

    private JsonNode post(String path, Map<String, Object> payload) {
        JsonNode response = client()
                .post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException(
                    "Janus returned an empty response"
            );
        }

        requireSuccess(response);
        return response;
    }

    private void requireSuccess(JsonNode response) {
        String status = response.path("janus").asText();

        if ("error".equals(status)) {
            throw new IllegalStateException(
                    "Janus error: " + response.path("error")
            );
        }

        if ("success".equals(status)
                || "server_info".equals(status)
                || "ack".equals(status)) {
            return;
        }

        throw new IllegalStateException(
                "Unexpected Janus response: " + response
        );
    }

    private void destroySession(long sessionId) {
        try {
            post(
                    "/" + sessionId,
                    Map.of(
                            "janus", "destroy",
                            "transaction", transaction()
                    )
            );
        } catch (RuntimeException exception) {
        }
    }

    private String transaction() {
        return java.util.UUID.randomUUID().toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
