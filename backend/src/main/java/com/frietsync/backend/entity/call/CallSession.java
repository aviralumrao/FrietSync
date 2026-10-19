package com.frietsync.backend.entity.call;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_sessions")
@Data
@NoArgsConstructor
public class CallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "room_id", nullable = false, unique = true)
    private Long roomId;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallStatus status = CallStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @PrePersist
    public void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public enum CallStatus {
        ACTIVE,
        ENDED
    }
}
