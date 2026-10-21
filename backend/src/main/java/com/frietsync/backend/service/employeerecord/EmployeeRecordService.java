package com.frietsync.backend.service.employeerecord;

import com.frietsync.backend.dto.employeerecord.EmployeeRecordResponse;
import com.frietsync.backend.dto.employeerecord.RecordActivityResponse;
import com.frietsync.backend.dto.employeerecord.RecordIssuesResponse;
import com.frietsync.backend.dto.employeerecord.RecordSprintsResponse;

import java.util.UUID;

public interface EmployeeRecordService {

    EmployeeRecordResponse overview(UUID userId, UUID viewerId);

    RecordIssuesResponse issues(UUID userId, UUID viewerId);

    RecordSprintsResponse sprints(UUID userId, UUID viewerId);

    RecordActivityResponse activity(UUID userId, UUID viewerId);
}