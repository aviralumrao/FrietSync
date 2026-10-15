package com.frietsync.backend.dto.poll;

import com.frietsync.backend.entity.poll.Poll;
import com.frietsync.backend.entity.poll.PollOption;
import com.frietsync.backend.entity.poll.PollVote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PollResponse(
        UUID id,
        UUID projectId,
        String question,
        String description,
        String status,
        UUID createdBy,
        UUID issueId,
        UUID sprintId,
        UUID milestoneId,
        Instant endsAt,
        Instant closedAt,
        String decisionRecord,
        Instant createdAt,
        List<Option> options,
        UUID myVote,
        List<UUID> myVotes
) {

    public record Option(
            UUID id,
            String label,
            int order,
            long votes
    ) {
    }

    public static PollResponse from(
            Poll poll,
            List<PollOption> options,
            List<PollVote> votes,
            UUID currentUserId
    ) {
        List<UUID> currentUserVotes = votes.stream()
                .filter(vote -> vote.getUserId().equals(currentUserId))
                .map(PollVote::getOptionId)
                .toList();
        return new PollResponse(
                poll.getId(),
                poll.getProjectId(),
                poll.getQuestion(),
                poll.getDescription(),
                poll.getStatus().name(),
                poll.getCreatedBy(),
                poll.getIssueId(),
                poll.getSprintId(),
                poll.getMilestoneId(),
                poll.getEndsAt(),
                poll.getClosedAt(),
                poll.getDecisionRecord(),
                poll.getCreatedAt(),
                options.stream()
                        .map(option -> new Option(
                                option.getId(),
                                option.getLabel(),
                                option.getOptionOrder(),
                                votes.stream()
                                        .filter(vote -> vote.getOptionId().equals(option.getId())).count()
                        ))
                        .toList(),
                currentUserVotes.isEmpty() ? null : currentUserVotes.get(0),
                currentUserVotes
        );
    }
}
