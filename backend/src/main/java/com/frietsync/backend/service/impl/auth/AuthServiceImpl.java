package com.frietsync.backend.service.impl.auth;

import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.config.security.JwtUtil;
import com.frietsync.backend.entity.auth.OtpPurpose;
import com.frietsync.backend.dto.auth.*;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.entity.user.Role;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.auth.AuthService;
import com.frietsync.backend.dto.user.UserResponse;
import com.frietsync.backend.service.impl.auth.OtpService;
import com.frietsync.backend.service.impl.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate redisTemplate;

    private static final Duration PENDING_SIGNUP_TTL = Duration.ofMinutes(15);

    @Override
    public void signup(SignupRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        otpService.enforceRequestRateLimit(email, ipAddress);
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null && existingUser.isActive()) {
            return;
        }

        PendingSignup pendingSignup = new PendingSignup(
                normalizeName(request.getName()),
                passwordEncoder.encode(request.getPassword()));
        redisTemplate.opsForValue().set(
                pendingSignupKey(email), serializePendingSignup(pendingSignup), PENDING_SIGNUP_TTL);
        otpService.sendOtp(email, OtpPurpose.SIGNUP);
    }

    @Override
    public void resendSignupOtp(ResendOtpRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        otpService.enforceRequestRateLimit(email, ipAddress);
        boolean hasPendingSignup = Boolean.TRUE.equals(redisTemplate.hasKey(pendingSignupKey(email)));
        boolean hasLegacyUnverifiedUser = userRepository.findByEmail(email)
                .filter(user -> !user.isActive())
                .isPresent();
        if (hasPendingSignup || hasLegacyUnverifiedUser) {
            otpService.sendOtp(email, OtpPurpose.SIGNUP);
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new BadRequestException(
                    "Please verify your email before logging in. Use /api/v1/auth/resend-otp to request a new code.");
        }

        String accessToken = jwtUtil.generateToken(
                user.getId(), user.getEmail(), user.getRole().name(), user.getPasswordVersion());

        String refreshToken = jwtUtil.generateRefreshToken();
        refreshTokenService.save(refreshToken, user.getId());

        return new AuthResponse(UserResponse.fromEntity(user), accessToken, refreshToken);
    }

    @Override
    public void verifySignupOtp(VerifyOtpRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        String pendingSignupKey = pendingSignupKey(email);
        String serializedPendingSignup = redisTemplate.opsForValue().get(pendingSignupKey);
        PendingSignup pendingSignup = deserializePendingSignup(serializedPendingSignup);
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (pendingSignup == null && (existingUser == null || existingUser.isActive())) {
            throw new BadRequestException("Signup has expired or was not found");
        }

        otpService.verifyOtp(email, request.getCode(), OtpPurpose.SIGNUP, ipAddress);

        if (pendingSignup == null) {
            existingUser.setActive(true);
            userRepository.save(existingUser);
            return;
        }

        User user = existingUser;
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setRole(Role.ADMIN);
        }

        user.setName(pendingSignup.name());
        user.setPasswordHash(pendingSignup.passwordHash());
        user.setActive(true);
        userRepository.save(user);
        redisTemplate.delete(pendingSignupKey);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        otpService.enforceRequestRateLimit(email, ipAddress);
        if (userRepository.existsByEmail(email)) {
            otpService.sendOtp(email, OtpPurpose.RESET_PASSWORD);
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        otpService.verifyOtp(email, request.getCode(), OtpPurpose.RESET_PASSWORD, ipAddress);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordVersion(user.getPasswordVersion() + 1);
        userRepository.save(user);

        refreshTokenService.deleteAllForUser(user.getId());
    }

    @Override
    public AuthResponse refreshAccessToken(String refreshToken) {
        UUID userId = refreshTokenService.useRefreshToken(refreshToken);

        if (userId == null) {
            throw new BadRequestException("Refresh token expired or revoked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        String accessToken = jwtUtil.generateToken(
                user.getId(), user.getEmail(), user.getRole().name(), user.getPasswordVersion());

        String newRefreshToken = jwtUtil.generateRefreshToken();
        refreshTokenService.save(newRefreshToken, user.getId());

        return new AuthResponse(UserResponse.fromEntity(user), accessToken, newRefreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        UUID userId = refreshTokenService.getUserId(refreshToken);

        if (userId != null) {
            refreshTokenService.delete(refreshToken);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        return Arrays.stream(name.trim().split("\\s+"))
                .map(word -> Character.toUpperCase(word.charAt(0))
                        + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String pendingSignupKey(String email) {
        return "pending-signup:" + email;
    }

    private String serializePendingSignup(PendingSignup pendingSignup) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedName = encoder.encodeToString(pendingSignup.name().getBytes(StandardCharsets.UTF_8));
        String encodedPasswordHash = encoder.encodeToString(
                pendingSignup.passwordHash().getBytes(StandardCharsets.UTF_8));
        return encodedName + ":" + encodedPasswordHash;
    }

    private PendingSignup deserializePendingSignup(String serializedPendingSignup) {
        if (serializedPendingSignup == null) {
            return null;
        }

        String[] fields = serializedPendingSignup.split(":", -1);
        if (fields.length != 2) {
            throw new IllegalStateException("Pending signup data in Redis is malformed");
        }

        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String name = new String(decoder.decode(fields[0]), StandardCharsets.UTF_8);
            String passwordHash = new String(decoder.decode(fields[1]), StandardCharsets.UTF_8);
            return new PendingSignup(name, passwordHash);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Pending signup data in Redis is malformed", ex);
        }
    }

    private record PendingSignup(String name, String passwordHash) {
    }
}