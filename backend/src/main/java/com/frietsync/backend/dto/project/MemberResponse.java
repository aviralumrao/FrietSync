package com.frietsync.backend.dto.project;

import com.frietsync.backend.entity.user.Role;
import lombok.Data;

import java.util.UUID;

@Data
public class MemberResponse {
    private UUID userId;
    private String name;
    private String email;
    private Role role;
}
