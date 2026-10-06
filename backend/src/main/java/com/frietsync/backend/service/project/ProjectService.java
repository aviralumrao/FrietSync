package com.frietsync.backend.service.project;

import com.frietsync.backend.dto.project.*;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ProjectResponse create(ProjectRequest request, UUID userId);
    List<ProjectResponse> myProjects(UUID userId);
    ProjectResponse get(UUID projectId, UUID userId);
    ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId);
    void delete(UUID projectId, UUID userId);
    List<MemberResponse> getMembers(UUID projectId, UUID userId);
    MemberResponse addMember(UUID projectId, MemberRequest request, UUID userId);
    void removeMember(UUID projectId, UUID memberUserId, UUID userId);
    MemberResponse assignRole(UUID projectId, MemberRequest request, UUID userId);
    LabelResponse createLabel(UUID projectId, LabelRequest request, UUID userId);
    List<LabelResponse> getLabels(UUID projectId, UUID userId);
}
