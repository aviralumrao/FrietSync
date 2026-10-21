package com.frietsync.backend.dto.employeerecord;

import com.frietsync.backend.entity.sprint.SprintStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RecordSprintItem {

    private UUID sprintId;
    private UUID projectId;
    private int sprintNumber;
    private String name;
    private SprintStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean completed;
}