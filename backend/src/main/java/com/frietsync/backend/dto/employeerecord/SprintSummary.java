package com.frietsync.backend.dto.employeerecord;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SprintSummary {

    private int committed;
    private int completed;
    private int active;
    private int planned;
    private Double completionRate;
}