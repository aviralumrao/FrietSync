package com.frietsync.backend.controller.user;

import com.frietsync.backend.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @DeleteMapping("/workspace/leave")
    public ResponseEntity<Map<String, String>> leaveWorkspace(
            @AuthenticationPrincipal UUID userId) {

        userService.leaveWorkspace(userId);

        return ResponseEntity.ok(
                Map.of("message", "Successfully left the workspace")
        );
    }
}