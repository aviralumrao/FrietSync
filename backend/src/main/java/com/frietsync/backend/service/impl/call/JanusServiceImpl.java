package com.frietsync.backend.service.impl.call;

import com.frietsync.backend.config.call.JanusConfig;
import com.frietsync.backend.entity.call.CallType;
import com.frietsync.backend.service.call.JanusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class JanusServiceImpl implements JanusService {

    private final JanusConfig janusConfig;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(janusConfig.baseUrl())
                .build();
    }

    @Override
    public long createRoom(CallType type) {
        long sessionId = createSession();
        long handleId = 0;

        try {
            handleId = type == CallType.VIDEO ? attachVideoRoom(sessionId) : attachAudioBridge(sessionId);

            long roomId = ThreadLocalRandom.current()
                    .nextLong(100_000_000L, 9_000_000_000L);

            Map<String, Object> room = new HashMap<>();
            room.put("request", "create");
            room.put("room", roomId);
            if (type == CallType.VIDEO) {
                room.put("description", "FrietSync video room");
                room.put("publishers", 16);
                room.put("videocodec", "vp8");
                room.put("audio", true);
                room.put("video", true);
            } else {
                room.put("description", "FrietSync voice room");
                room.put("sampling_rate", 16000);
                room.put("record", false);
                room.put("audiolevel_event", true);
            }

            if (hasText(janusConfig.adminKey())) {
                room.put("admin_key", janusConfig.adminKey());
            }

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
            String pluginKey = type == CallType.VIDEO ? "videoroom" : "audiobridge";

            if (!"created".equals(data.path(pluginKey).asText())
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
    public void destroyRoom(long roomId, CallType type) {
        long sessionId = createSession();

        try {
            long handleId = type == CallType.VIDEO ? attachVideoRoom(sessionId) : attachAudioBridge(sessionId);

            Map<String, Object> existenceRequest = new HashMap<>();
            existenceRequest.put("request", "exists");
            existenceRequest.put("room", roomId);

            if (!roomExists(sessionId, handleId, existenceRequest, roomId, type)) {
                return;
            }

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
            String pluginKey = type == CallType.VIDEO ? "videoroom" : "audiobridge";
            String result = data.path(pluginKey).asText();

            if (!"destroyed".equals(result)) {
                throw new IllegalStateException(
                        "Janus did not confirm room destruction: " + response
                );
            }

        } finally {
            destroySession(sessionId);
        }
    }

    private boolean roomExists(long sessionId, long handleId, Map<String, Object> request, long roomId, CallType type) {
        JsonNode response = post(
                "/%d/%d".formatted(sessionId, handleId),
                Map.of(
                        "janus", "message",
                        "transaction", transaction(),
                        "body", request
                )
        );
        JsonNode data = response.path("plugindata").path("data");
        String pluginKey = type == CallType.VIDEO ? "videoroom" : "audiobridge";
        if (!"success".equals(data.path(pluginKey).asText())
                || data.path("room").asLong() != roomId
                || !data.path("exists").isBoolean()) {
            throw new IllegalStateException("Janus returned an invalid room-existence response: " + response);
        }
        return data.path("exists").asBoolean();
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

    private long attachVideoRoom(long sessionId) {
        JsonNode response = post(
                "/" + sessionId,
                Map.of(
                        "janus", "attach",
                        "plugin", "janus.plugin.videoroom",
                        "transaction", transaction()
                )
        );

        requireSuccess(response);

        long handleId = response.path("data").path("id").asLong();

        if (handleId <= 0) {
            throw new IllegalStateException(
                    "Janus returned an invalid videoroom plugin handle ID"
            );
        }

        return handleId;
    }

    private JsonNode post(String path, Map<String, Object> payload) {
        JsonNode response = client()
                .post()
                .uri(uriBuilder -> uriBuilder.path(path).build())
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
            log.warn("Could not destroy Janus session {}", sessionId, exception);
        }
    }

    private String transaction() {
        return java.util.UUID.randomUUID().toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}