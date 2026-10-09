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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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

    @Override
    public void signup(SignupRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        otpService.enforceRequestRateLimit(email, ipAddress);
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser == null) {
            String hashedPassword = passwordEncoder.encode(request.getPassword());

            User user = new User();
            user.setName(normalizeName(request.getName()));
            user.setEmail(email);
            user.setPasswordHash(hashedPassword);
            user.setWorkspaceId(UUID.randomUUID());
            user.setRole(Role.ADMIN);
            user.setActive(false);

            existingUser = userRepository.save(user);
        }

        if (!existingUser.isActive()) {
            otpService.sendOtp(email, OtpPurpose.SIGNUP);
        }
    }

    @Override
    public void resendSignupOtp(ResendOtpRequest request, String ipAddress) {
        String email = normalizeEmail(request.getEmail());
        otpService.enforceRequestRateLimit(email, ipAddress);
        userRepository.findByEmail(email)
                .filter(user -> !user.isActive())
                .ifPresent(user -> otpService.sendOtp(email, OtpPurpose.SIGNUP));
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
        otpService.verifyOtp(email, request.getCode(), OtpPurpose.SIGNUP, ipAddress);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        user.setActive(true);
        userRepository.save(user);
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
}