package com.frietsync.backend.user.service;

import com.frietsync.backend.user.dto.*;

public interface AuthService {
    UserResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    void verifySignupOtp(VerifyOtpRequest request);
}