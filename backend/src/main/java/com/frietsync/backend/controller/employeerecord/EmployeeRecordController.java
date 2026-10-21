package com.frietsync.backend.controller.employeerecord;

import com.frietsync.backend.dto.employeerecord.EmployeeRecordResponse;
import com.frietsync.backend.dto.employeerecord.RecordActivityResponse;
import com.frietsync.backend.dto.employeerecord.RecordIssuesResponse;
import com.frietsync.backend.dto.employeerecord.RecordSprintsResponse;
import com.frietsync.backend.service.employeerecord.EmployeeRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmployeeRecordController {

    private final EmployeeRecordService employeeRecordService;

    @GetMapping("/users/{userId}/record")
    public ResponseEntity<EmployeeRecordResponse> overview(@PathVariable UUID userId,
                                                           @AuthenticationPrincipal UUID viewerId) {
        return ResponseEntity.ok(employeeRecordService.overview(userId, viewerId));
    }

    @GetMapping("/users/{userId}/record/issues")
    public ResponseEntity<RecordIssuesResponse> issues(@PathVariable UUID userId,
                                                       @AuthenticationPrincipal UUID viewerId) {
        return ResponseEntity.ok(employeeRecordService.issues(userId, viewerId));
    }

    @GetMapping("/users/{userId}/record/sprints")
    public ResponseEntity<RecordSprintsResponse> sprints(@PathVariable UUID userId,
                                                         @AuthenticationPrincipal UUID viewerId) {
        return ResponseEntity.ok(employeeRecordService.sprints(userId, viewerId));
    }

    @GetMapping("/users/{userId}/record/activity")
    public ResponseEntity<RecordActivityResponse> activity(@PathVariable UUID userId,
                                                           @AuthenticationPrincipal UUID viewerId) {
        return ResponseEntity.ok(employeeRecordService.activity(userId, viewerId));
    }
}