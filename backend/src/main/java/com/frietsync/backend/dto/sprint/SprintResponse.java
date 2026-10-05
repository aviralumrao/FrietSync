package com.frietsync.backend.dto.sprint;

import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.entity.sprint.SprintStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class SprintResponse {
    private UUID id;
    private UUID projectId;
    private Integer sprintNumber;
    private String name;
    private String goal;
    private SprintStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID createdBy;
    private UUID assignedContributorId;
    private Instant createdAt;
    private Instant updatedAt;

    public static SprintResponse fromEntity(Sprint sprint) {
        SprintResponse response = new SprintResponse();
        response.setId(sprint.getId());
        response.setProjectId(sprint.getProjectId());
        response.setSprintNumber(sprint.getSprintNumber());
        response.setName(sprint.getName());
        response.setGoal(sprint.getGoal());
        response.setStatus(sprint.getStatus());
        response.setStartDate(sprint.getStartDate());
        response.setEndDate(sprint.getEndDate());
        response.setCreatedBy(sprint.getCreatedBy());
        response.setAssignedContributorId(sprint.getAssignedContributorId());
        response.setCreatedAt(sprint.getCreatedAt());
        response.setUpdatedAt(sprint.getUpdatedAt());
        return response;
    }
}