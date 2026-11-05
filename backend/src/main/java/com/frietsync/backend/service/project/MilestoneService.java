












package com.frietsync.backend.service.project;

import com.frietsync.backend.dto.project.MilestoneRequest;
import com.frietsync.backend.dto.project.MilestoneResponse;
import com.frietsync.backend.dto.project.UpdateMilestoneRequest;

import java.util.List;
import java.util.UUID;

public interface MilestoneService {

    MilestoneResponse create(UUID projectId, UUID userId, MilestoneRequest request);

    List<MilestoneResponse> list(UUID projectId, UUID userId);

    MilestoneResponse update(UUID milestoneId, UUID userId, UpdateMilestoneRequest request);

    void delete(UUID milestoneId, UUID userId);

    MilestoneResponse restore(UUID milestoneId, UUID userId);
}
