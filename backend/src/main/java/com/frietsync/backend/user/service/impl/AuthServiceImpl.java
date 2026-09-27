package com.frietsync.backend.user.service.impl;

import com.frietsync.backend.common.exception.BadRequestException;
import com.frietsync.backend.common.security.JwtUtil;
import com.frietsync.backend.user.dto.AuthResponse;
import com.frietsync.backend.user.dto.ForgotPasswordRequest;
import com.frietsync.backend.user.dto.LoginRequest;
import com.frietsync.backend.user.dto.ResetPasswordRequest;
import com.frietsync.backend.user.dto.SignupRequest;
import com.frietsync.backend.user.dto.UserResponse;
import com.frietsync.backend.user.dto.VerifyOtpRequest;
import com.frietsync.backend.user.entity.User;
import com.frietsync.backend.user.enums.Role;
import com.frietsync.backend.user.otp.enums.OtpPurpose;
import com.frietsync.backend.user.otp.service.OtpService;
import com.frietsync.backend.user.repository.UserRepository;
import com.frietsync.backend.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;

    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(hashedPassword);
        user.setRole(Role.ADMIN);
        user.setActive(true);

        User savedUser = userRepository.save(user);

        return UserResponse.fromEntity(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> foundUser = userRepository.findByEmail(request.getEmail());

        if (foundUser.isEmpty()) {
            throw new BadRequestException("Invalid email or password");
        }

        User user = foundUser.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return new AuthResponse(UserResponse.fromEntity(user), token);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        boolean emailExists = userRepository.existsByEmail(request.getEmail());

        if (!emailExists) {
            throw new BadRequestException("No account found with this email, please sign up");
        }

        otpService.sendOtp(request.getEmail(), OtpPurpose.RESET_PASSWORD);
    }

    public void verifyResetOtp(VerifyOtpRequest request) {
        boolean otpIsCorrect = otpService.isOtpValid(request.getEmail(), request.getCode(), OtpPurpose.RESET_PASSWORD);

        if (!otpIsCorrect) {
            throw new BadRequestException("Invalid or expired OTP");
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        Optional<User> foundUser = userRepository.findByEmail(request.getEmail());

        if (foundUser.isEmpty()) {
            throw new BadRequestException("No account found with this email");
        }

        User user = foundUser.get();

        otpService.verifyOtp(request.getEmail(), request.getCode(), OtpPurpose.RESET_PASSWORD);

        String hashedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPasswordHash(hashedPassword);
        userRepository.save(user);
    }
}