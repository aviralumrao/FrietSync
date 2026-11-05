package com.frietsync.backend.service.project;

import com.frietsync.backend.dto.project.MemberResponse;
import com.frietsync.backend.dto.project.ProjectInviteRequest;
import com.frietsync.backend.dto.invite.InviteResponse;
import com.frietsync.backend.dto.project.ProjectRequest;
import com.frietsync.backend.dto.project.ProjectResponse;
import com.frietsync.backend.dto.project.ProjectListResponse;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectService {
    ProjectResponse create(ProjectRequest request, UUID userId);
    List<ProjectListResponse> myProjects(UUID userId);
    ProjectResponse get(UUID projectId, UUID userId);
    ProjectResponse update(UUID projectId, ProjectRequest request, UUID userId);
    ProjectResponse uploadProfilePicture(UUID projectId, MultipartFile profilePicture, UUID userId);
    ProjectResponse removeProfilePicture(UUID projectId, UUID userId);
    void delete(UUID projectId, UUID userId);
    ProjectResponse restore(UUID projectId, UUID userId);
    List<MemberResponse> getMembers(UUID projectId, UUID userId);
    InviteResponse inviteMember(UUID projectId, ProjectInviteRequest request, UUID userId);
    void removeMember(UUID projectId, UUID memberUserId, UUID userId);

}
