
package com.frietsync.backend.user.otp.repository;

import com.frietsync.backend.otp.entity.Otp;
import com.frietsync.backend.otp.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OtpRepository extends JpaRepository<Otp, UUID> {
    List<Otp> findByEmailAndPurpose(String email, OtpPurpose purpose);
}
