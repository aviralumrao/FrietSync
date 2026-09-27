package com.frietsync.backend.user.service;

import com.frietsync.backend.user.dto.AuthResponse;
import com.frietsync.backend.user.dto.ForgotPasswordRequest;
import com.frietsync.backend.user.dto.LoginRequest;
import com.frietsync.backend.user.dto.ResetPasswordRequest;
import com.frietsync.backend.user.dto.SignupRequest;
import com.frietsync.backend.user.dto.UserResponse;
import com.frietsync.backend.user.dto.VerifyOtpRequest;

public interface AuthService {
    UserResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void verifyResetOtp(VerifyOtpRequest request);
    void resetPassword(ResetPasswordRequest request);
}