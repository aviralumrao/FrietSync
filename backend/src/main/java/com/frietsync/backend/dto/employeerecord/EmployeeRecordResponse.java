package com.frietsync.backend.dto.employeerecord;

import com.frietsync.backend.entity.user.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class EmployeeRecordResponse {

    private UUID userId;
    private String name;
    private String email;
    private Role role;
    private String jobTitle;
    private String bio;
    private String githubUrl;
    private boolean active;
    private int projectCount;
    private IssueSummary issues;
    private SprintSummary sprints;
    private RecordActivityResponse activity;
}