package com.frietsync.backend.dto.project;

import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ProjectResponse {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate deadline;
    private LocalDate completeDate;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private int issueCounter;
    private int sprintCounter;
    private int teamSize;
    private UUID adminId;
    private UUID projectManagerId;
    private UUID teamLeadId;

    public static ProjectResponse fromEntity(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .workspaceId(p.getWorkspaceId())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus())
                .startDate(p.getStartDate())
                .deadline(p.getDeadline())
                .completeDate(p.getCompleteDate())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .issueCounter(p.getIssueCounter())
                .sprintCounter(p.getSprintCounter())
                .teamSize(p.getTeamSize())
                .adminId(p.getAdminId())
                .projectManagerId(p.getProjectManagerId())
                .teamLeadId(p.getTeamLeadId())
                .build();
    }
}