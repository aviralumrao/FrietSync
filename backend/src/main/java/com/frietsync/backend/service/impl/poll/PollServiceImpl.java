package com.frietsync.backend.service.impl.poll;

import com.frietsync.backend.dto.poll.CreatePollRequest;
import com.frietsync.backend.dto.poll.PollResponse;
import com.frietsync.backend.dto.poll.PollUpdateEvent;
import com.frietsync.backend.dto.poll.VoteRequest;
import com.frietsync.backend.entity.poll.Poll;
import com.frietsync.backend.entity.poll.PollMode;
import com.frietsync.backend.entity.poll.PollOption;
import com.frietsync.backend.entity.poll.PollStatus;
import com.frietsync.backend.entity.poll.PollVote;
import com.frietsync.backend.entity.project.Project;
import com.frietsync.backend.entity.project.Milestone;
import com.frietsync.backend.entity.issue.Issue;
import com.frietsync.backend.entity.sprint.Sprint;
import com.frietsync.backend.exception.BadRequestException;
import com.frietsync.backend.exception.ForbiddenException;
import com.frietsync.backend.exception.ResourceNotFoundException;
import com.frietsync.backend.repository.poll.PollOptionRepository;
import com.frietsync.backend.repository.poll.PollRepository;
import com.frietsync.backend.repository.poll.PollVoteRepository;
import com.frietsync.backend.repository.issue.IssueRepository;
import com.frietsync.backend.repository.project.MilestoneRepository;
import com.frietsync.backend.repository.project.ProjectMemberRepository;
import com.frietsync.backend.repository.project.ProjectRepository;
import com.frietsync.backend.repository.sprint.SprintRepository;
import com.frietsync.backend.service.poll.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PollServiceImpl implements PollService {
    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final PollVoteRepository voteRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final SprintRepository sprintRepository;
    private final IssueRepository issueRepository;
    private final MilestoneRepository milestoneRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public PollResponse create(UUID projectId, CreatePollRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        requireMember(project, userId);
        if (request.getEndsAt() != null && !request.getEndsAt().isAfter(Instant.now())) {
            throw new BadRequestException("Poll deadline must be in the future");
        }
        int linkedObjectCount = (request.getIssueId() == null ? 0 : 1)
                + (request.getSprintId() == null ? 0 : 1)
                + (request.getMilestoneId() == null ? 0 : 1);
        if (linkedObjectCount > 1) {
            throw new BadRequestException("A poll can be linked to only one issue, sprint, or milestone");
        }
        Set<String> labels = new HashSet<>();
        for (String label : request.getOptions()) {
            if (!labels.add(label.trim().toLowerCase())) {
                throw new BadRequestException("Poll options must be unique");
            }
        }
        if (request.getSprintId() != null) {
            Sprint sprint = sprintRepository.findById(request.getSprintId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sprint not found"));
            if (!projectId.equals(sprint.getProjectId())) {
                throw new BadRequestException("Linked sprint must belong to this project");
            }
        }
        if (request.getIssueId() != null) {
            Issue issue = issueRepository.findById(request.getIssueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Issue not found"));
            if (!projectId.equals(issue.getProjectId())) {
                throw new BadRequestException("Linked issue must belong to this project");
            }
        }
        if (request.getMilestoneId() != null) {
            Milestone milestone = milestoneRepository.findById(request.getMilestoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
            if (!projectId.equals(milestone.getProjectId())) {
                throw new BadRequestException("Linked milestone must belong to this project");
            }
        }
        Poll poll = new Poll();
        poll.setProjectId(projectId);
        poll.setQuestion(request.getQuestion().trim());
        poll.setDescription(trimToNull(request.getDescription()));
        poll.setMode(request.getMode() == null ? PollMode.SINGLE : request.getMode());
        poll.setCreatedBy(userId);
        poll.setIssueId(request.getIssueId());
        poll.setSprintId(request.getSprintId());
        poll.setMilestoneId(request.getMilestoneId());
        poll.setEndsAt(request.getEndsAt());
        poll = pollRepository.save(poll);
        List<PollOption> options = new ArrayList<>();
        for (int index = 0; index < request.getOptions().size(); index++) {
            PollOption option = new PollOption();
            option.setPollId(poll.getId());
            option.setLabel(request.getOptions().get(index).trim());
            option.setOptionOrder(index);
            options.add(optionRepository.save(option));
        }
        PollResponse result = response(poll, userId);
        publishAfterCommit("POLL_CREATED", result);
        return result;
    }

    @Override
    @Transactional
    public List<PollResponse> list(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        requireMember(project, userId);
        return pollRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(poll -> response(closeIfExpired(poll), userId)).toList();
    }

    @Override
    @Transactional
    public PollResponse get(UUID pollId, UUID userId) {
        Poll poll = findPoll(pollId);
        requireMember(findProject(poll.getProjectId()), userId);
        poll = closeIfExpired(poll);
        return response(poll, userId);
    }

    @Override
    @Transactional
    public PollResponse vote(UUID pollId, VoteRequest request, UUID userId) {
        Poll poll = pollRepository.findByIdForUpdate(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));
        requireMember(findProject(poll.getProjectId()), userId);
        if (poll.getStatus() == PollStatus.OPEN && poll.getEndsAt() != null
                && !Instant.now().isBefore(poll.getEndsAt())) {
            throw new BadRequestException("This poll has expired");
        }
        requireOpen(poll);
        List<PollOption> options = optionRepository.findByPollIdOrderByOptionOrder(pollId);
        Set<UUID> validIds = new HashSet<>();
        options.forEach(option -> validIds.add(option.getId()));
        List<UUID> selectedIds;
        if (request.getOptionIds() != null) {
            if (request.getOptionId() != null) {
                throw new BadRequestException("Use optionId or optionIds, not both");
            }
            selectedIds = request.getOptionIds();
        } else if (request.getOptionId() != null) {
            selectedIds = List.of(request.getOptionId());
        } else {
            throw new BadRequestException("At least one option must be selected");
        }
        if (selectedIds.isEmpty()) {
            throw new BadRequestException("At least one option must be selected");
        }
        Set<UUID> selectedSet = new HashSet<>(selectedIds);
        if (selectedSet.size() != selectedIds.size()) {
            throw new BadRequestException("An option cannot be selected more than once");
        }
        if (poll.getMode() == PollMode.SINGLE && selectedIds.size() != 1) {
            throw new BadRequestException("Single-choice polls accept exactly one option");
        }
        if (!validIds.containsAll(selectedIds)) {
            throw new BadRequestException("Every selected option must belong to this poll");
        }
        voteRepository.deleteByPollIdAndUserId(pollId, userId);
        for (UUID optionId : selectedIds) {
            PollVote vote = new PollVote();
            vote.setPollId(pollId);
            vote.setOptionId(optionId);
            vote.setUserId(userId);
            voteRepository.save(vote);
        }
        PollResponse result = response(poll, userId);
        publishAfterCommit("POLL_UPDATED", result);
        return result;
    }

    @Override
    @Transactional
    public PollResponse close(UUID pollId, String decisionRecord, UUID userId) {
        Poll poll = pollRepository.findByIdForUpdate(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));
        requireMember(findProject(poll.getProjectId()), userId);
        Project project = findProject(poll.getProjectId());
        if (!poll.getCreatedBy().equals(userId) && !Objects.equals(project.getProjectManagerId(), userId)
                && !Objects.equals(project.getAdminId(), userId)) {
            throw new ForbiddenException("Only the poll creator or project manager can close this poll");
        }
        if (poll.getStatus() == PollStatus.OPEN) {
            poll.setDecisionRecord(trimToNull(decisionRecord));
            closePoll(poll);
            publishAfterCommit("POLL_CLOSED", response(poll, userId));
        } else if (decisionRecord != null && !decisionRecord.isBlank()) {
            throw new BadRequestException("A closed poll decision record cannot be changed");
        }
        return response(poll, userId);
    }

    @Scheduled(fixedDelayString = "${app.polls.expiry.delay-ms:10000}")
    @Transactional
    public void closeExpiredPolls() {
        Instant now = Instant.now();
        List<Poll> expiredPolls = pollRepository
                .findByStatusAndEndsAtLessThanEqual(PollStatus.OPEN, now);
        for (Poll expiredPoll : expiredPolls) {
            var pollResult = pollRepository.findByIdForUpdate(expiredPoll.getId());
            if (pollResult.isEmpty()) continue;
            Poll poll = pollResult.get();
            if (poll.getStatus() == PollStatus.OPEN && poll.getEndsAt() != null
                    && !poll.getEndsAt().isAfter(now)) {
                closePoll(poll);
                publishAfterCommit("POLL_CLOSED", response(poll, poll.getCreatedBy()));
            }
        }
    }

    private Poll closeIfExpired(Poll poll) {
        if (poll.getStatus() == PollStatus.OPEN && poll.getEndsAt() != null
                && !Instant.now().isBefore(poll.getEndsAt())) {
            poll = pollRepository.findByIdForUpdate(poll.getId()).orElse(poll);
            if (poll.getStatus() == PollStatus.OPEN) {
                closePoll(poll);
                publishAfterCommit("POLL_CLOSED", response(poll, poll.getCreatedBy()));
            }
        }
        return poll;
    }

    private void closePoll(Poll poll) {
        poll.setStatus(PollStatus.CLOSED);
        poll.setClosedAt(Instant.now());
        pollRepository.save(poll);
    }

    private void publishAfterCommit(String type, PollResponse poll) {
        afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/polls/" + poll.id() + "/results", new PollUpdateEvent(type, poll)));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void requireMember(Project project, UUID userId) {
        if (userId == null || !(userId.equals(project.getCreatedBy())
                || userId.equals(project.getAdminId()) || userId.equals(project.getProjectManagerId())
                || userId.equals(project.getTeamLeadId())
                || memberRepository.existsByProjectIdAndUserId(project.getId(), userId))) {
            throw new ForbiddenException("You are not a member of this project");
        }
    }

    private PollResponse response(Poll poll, UUID userId) {
        return PollResponse.from(poll, optionRepository.findByPollIdOrderByOptionOrder(poll.getId()),
                voteRepository.findByPollId(poll.getId()), userId);
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private Poll findPoll(UUID pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));
    }

    private void requireOpen(Poll poll) {
        if (poll.getStatus() != PollStatus.OPEN) throw new BadRequestException("This poll is closed");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
