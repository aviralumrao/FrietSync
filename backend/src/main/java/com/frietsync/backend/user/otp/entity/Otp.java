package com.frietsync.backend.user.otp.entity;


import com.frietsync.backend.common.entity.BaseEntity;
import com.frietsync.backend.otp.enums.OtpPurpose;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "otps")
@Data
public class Otp extends BaseEntity {

    private String email;
    private String code;

    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    private Instant expiresAt;

    private boolean used = false;
}
