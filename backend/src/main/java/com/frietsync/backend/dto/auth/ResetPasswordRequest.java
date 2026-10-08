package com.frietsync.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^\\d{6}$",
            message = "OTP must be exactly 6 digits"
    )
    private String code;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$",
            message = "Password must contain uppercase, lowercase, number and symbol"
    )
    private String newPassword;
}
