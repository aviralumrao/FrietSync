package com.frietsync.backend.service.project;

import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ProjectResponse create(ProjectRequest request, UUID userId);
    List<ProjectResponse> myProjects(UUID userId);
    ProjectResponse get(UUID projectId, UUID userId);
    ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId);
    void delete(UUID projectId, UUID userId);

}
