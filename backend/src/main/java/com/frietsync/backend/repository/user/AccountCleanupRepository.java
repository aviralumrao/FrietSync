package com.frietsync.backend.repository.user;

import com.frietsync.backend.entity.user.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountCleanupRepository extends Repository<User, UUID> {

    @Modifying
    @Query("delete from ProjectMember m where m.userId = :userId")
    void deleteProjectMemberships(@Param("userId") UUID userId);

    @Modifying
    @Query("update IssueAssignee a set a.assignedBy = null where a.assignedBy = :userId")
    void clearIssueAssigner(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from IssueAssignee a where a.userId = :userId")
    void deleteIssueAssignments(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from MeetingAttendee a where a.userId = :userId")
    void deleteMeetingAttendance(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from IssueAssignmentRequest r where r.userId = :userId")
    void deleteIssueAssignmentRequests(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from SprintAssignmentRequest r where r.requestedBy = :userId")
    void deleteSprintAssignmentRequests(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from PollVote v where v.userId = :userId")
    void deletePollVotes(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    void deleteNotifications(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from NotificationPreference p where p.userId = :userId")
    void deleteNotificationPreferences(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from IssueComment c where c.userId = :userId")
    void deleteIssueComments(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from SprintComment c where c.userId = :userId")
    void deleteSprintComments(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from Invite i where i.invitedBy = :userId")
    void deleteInvites(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from Invite i where lower(i.email) = lower(:email)")
    void deleteInvitesForEmail(@Param("email") String email);

    @Modifying
    @Query("delete from GoogleOAuthState s where s.user.id = :userId")
    void deleteGoogleOAuthStates(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from GoogleConnection c where c.user.id = :userId")
    void deleteGoogleConnection(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from ProjectDocument d where d.createdBy = :userId")
    void deleteProjectDocumentsCreatedBy(@Param("userId") UUID userId);

    @Modifying
    @Query("update ProjectDocument d set d.lastEditedBy = d.createdBy "
            + "where d.lastEditedBy = :userId and d.createdBy <> :userId")
    void clearProjectDocumentEditor(@Param("userId") UUID userId);

    @Modifying
    @Query("update ChatMessage m set m.replyTo = null where m.replyTo.senderId = :userId")
    void clearRepliesToUserMessages(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from ChatMessage m where m.senderId = :userId")
    void deleteChatMessages(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from IssueHistory h where h.actorId = :userId")
    void deleteIssueHistoryByActor(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from IssueLink l where l.createdBy = :userId")
    void deleteIssueLinksCreatedBy(@Param("userId") UUID userId);

    @Modifying
    @Query("update Project p set p.adminId = :replacementId, p.projectManagerId = case when p.projectManagerId = :userId then :replacementId else p.projectManagerId end, p.teamLeadId = case when p.teamLeadId = :userId then :replacementId else p.teamLeadId end, p.createdBy = :replacementId where p.adminId = :userId or p.projectManagerId = :userId or p.teamLeadId = :userId or p.createdBy = :userId")
    void replaceProjectUserReferences(@Param("userId") UUID userId, @Param("replacementId") UUID replacementId);

    @Modifying
    @Query("update Sprint s set s.assignedContributorId = null where s.assignedContributorId = :userId")
    void clearSprintAssignment(@Param("userId") UUID userId);

    @Modifying
    @Query("update Meeting m set m.organizerId = :replacementId where m.organizerId = :userId")
    void replaceMeetingOrganizer(@Param("userId") UUID userId, @Param("replacementId") UUID replacementId);

    @Modifying
    @Query("update Issue i set i.createdBy = :replacementId where i.createdBy = :userId")
    void replaceIssueCreator(@Param("userId") UUID userId, @Param("replacementId") UUID replacementId);

    @Modifying
    @Query("update Sprint s set s.createdBy = :replacementId where s.createdBy = :userId")
    void replaceSprintCreator(@Param("userId") UUID userId, @Param("replacementId") UUID replacementId);

    @Modifying
    @Query("update Poll p set p.createdBy = :replacementId where p.createdBy = :userId")
    void replacePollCreator(@Param("userId") UUID userId, @Param("replacementId") UUID replacementId);

    @Modifying
    @Query("update IssueAssignmentRequest r set r.decidedBy = null where r.decidedBy = :userId")
    void clearIssueDecisionMaker(@Param("userId") UUID userId);

    @Modifying
    @Query("update SprintAssignmentRequest r set r.reviewedBy = null where r.reviewedBy = :userId")
    void clearSprintReviewer(@Param("userId") UUID userId);

    @Modifying
    @Query("update Notification n set n.actorId = null where n.actorId = :userId")
    void clearNotificationActor(@Param("userId") UUID userId);
}
