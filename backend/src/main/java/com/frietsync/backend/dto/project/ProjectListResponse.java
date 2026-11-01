package com.frietsync.backend.dto.project;

import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ProjectListResponse {

    private UUID id;
    private String name;
    private String profilePictureUrl;
    private UUID adminId;
    private String description;
    private int memberCount;
    private LocalDate deadline;
    private LocalDate startDate;
    private LocalDate completeDate;
    private long sprintCount;
    private long issueCount;
    private double progressPercentage;
    private ProjectStatus status;

    public static ProjectListResponse fromEntity(Project project, long sprintCount,
                                                 long issueCount, double progressPercentage) {
        return ProjectListResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .adminId(project.getAdminId())
                .description(project.getDescription())
                .memberCount(project.getTeamSize())
                .deadline(project.getDeadline())
                .startDate(project.getStartDate())
                .completeDate(project.getCompleteDate())
                .sprintCount(sprintCount)
                .issueCount(issueCount)
                .progressPercentage(progressPercentage)
                .status(project.getStatus())
                .build();
    }
}
