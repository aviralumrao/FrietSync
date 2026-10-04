package com.frietsync.backend.entity.project;

import com.frietsync.backend.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.PLANNING;

    @Column(name = "start_date")
    private LocalDate startDate;

    private LocalDate deadline;

    @Column(name = "complete_date")
    private LocalDate completeDate;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "issue_counter", nullable = false)
    private int issueCounter = 0;

    @Column(name = "sprint_counter", nullable = false)
    private int sprintCounter = 0;

    @Column(name = "team_size", nullable = false)
    private int teamSize = 1;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "project_manager_id")
    private UUID projectManagerId;

    @Column(name = "team_lead_id")
    private UUID teamLeadId;
}
