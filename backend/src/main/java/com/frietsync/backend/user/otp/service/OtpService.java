
package com.frietsync.backend.user.otp.service;

import com.frietsync.backend.common.exception.BadRequestException;
import com.frietsync.backend.common.mail.EmailServiceImpl;
import com.frietsync.backend.otp.entity.Otp;
import com.frietsync.backend.otp.enums.OtpPurpose;
import com.frietsync.backend.otp.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailServiceImpl emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public void sendOtp(String email, OtpPurpose purpose) {
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);

        Otp otp = new Otp();
        otp.setEmail(email);
        otp.setCode(code);
        otp.setPurpose(purpose);
        otp.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        otp.setUsed(false);

        otpRepository.save(otp);
        emailService.sendOtpMail(email, code);
    }

    public void verifyOtp(String email, String code, OtpPurpose purpose) {
        List<Otp> otps = otpRepository.findByEmailAndPurpose(email, purpose);

        Otp otp = otps.stream()
                .filter(o -> !o.isUsed())
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No OTP found for this email"));

        if (Instant.now().isAfter(otp.getExpiresAt())) {
            throw new BadRequestException("OTP has expired");
        }

        if (!otp.getCode().equals(code)) {
            throw new BadRequestException("Invalid OTP");
        }

        otp.setUsed(true);
        otpRepository.save(otp);
    }
    public boolean isOtpValid(String email, String code, OtpPurpose purpose) {
        List<Otp> allOtpsForThisEmail = otpRepository.findByEmailAndPurpose(email, purpose);
        for (Otp otp : allOtpsForThisEmail) {
            if (otp.isUsed()) {
                continue;
            }
            if (Instant.now().isAfter(otp.getExpiresAt())) {
                continue;
            }
            if (otp.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

}
