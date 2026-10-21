package com.frietsync.backend.dto.employeerecord;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueSummary {

    private int created;
    private int resolved;
    private int reopened;
    private Double averageHoursToResolve;
}