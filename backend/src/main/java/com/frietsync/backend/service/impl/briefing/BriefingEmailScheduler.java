package com.frietsync.backend.service.impl.briefing;

import com.frietsync.backend.dto.activity.ActivityResponse;
import com.frietsync.backend.dto.briefing.BriefingMeetingResponse;
import com.frietsync.backend.dto.briefing.DailyBriefingResponse;
import com.frietsync.backend.dto.issue.IssueSummaryResponse;
import com.frietsync.backend.entity.briefing.BriefingPreference;
import com.frietsync.backend.entity.user.User;
import com.frietsync.backend.repository.briefing.BriefingPreferenceRepository;
import com.frietsync.backend.repository.user.UserRepository;
import com.frietsync.backend.service.briefing.BriefingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class BriefingEmailScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(BriefingEmailScheduler.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_ASSIGNED_IN_EMAIL = 10;

    private final BriefingPreferenceRepository preferenceRepository;
    private final BriefingService briefingService;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Scheduled(cron = "0 * * * * *")
    public void sendDueBriefings() {
        List<BriefingPreference> preferences = preferenceRepository.findByEmailEnabledTrue();
        for (BriefingPreference preference : preferences) {
            try {
                sendIfDue(preference);
            } catch (Exception ex) {
                LOG.warn("Could not send the daily briefing to user {}: {}", preference.getUserId(),
                        ex.getMessage());
            }
        }
    }

    private void sendIfDue(BriefingPreference preference) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(preference.getTimeZone()));
        LocalDate today = now.toLocalDate();

        if (today.equals(preference.getLastSentOn())) {
            return;
        }
        if (now.toLocalTime().isBefore(preference.getDeliveryTime())) {
            return;
        }

        User user = userRepository.findById(preference.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }

        DailyBriefingResponse briefing = briefingService.today(preference.getUserId());

        preference.setLastSentOn(today);
        preferenceRepository.save(preference);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(user.getEmail());
            helper.setSubject("Your Daily Briefing — " + briefing.getDate());
            helper.setText(emailHtmlBody(briefing), true);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            LOG.error("Failed to send daily briefing email: {}", ex.getMessage(), ex);
        }
    }

    private String emailHtmlBody(DailyBriefingResponse briefing) {
        String name = briefing.getUserName();
        if (name == null || name.isBlank()) {
            name = "there";
        }
        ZoneId zone = ZoneId.of(briefing.getTimeZone());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<style>");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f6f8; color: #333333; margin: 0; padding: 0; }");
        html.append(".container { max-width: 600px; margin: 20px auto; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        html.append(".header { background: #2563eb; color: #ffffff; padding: 24px; text-align: center; }");
        html.append(".header h1 { margin: 0; font-size: 22px; font-weight: 600; }");
        html.append(".header p { margin: 8px 0 0; font-size: 14px; opacity: 0.9; }");
        html.append(".content { padding: 24px; }");
        html.append(".greeting { font-size: 16px; font-weight: 600; margin-bottom: 16px; }");
        html.append(".headline { background: #eff6ff; color: #1e40af; padding: 12px 16px; border-radius: 6px; font-size: 14px; margin-bottom: 24px; font-weight: 500; }");
        html.append(".section { margin-bottom: 24px; }");
        html.append(".section h3 { font-size: 15px; color: #1e293b; border-bottom: 2px solid #e2e8f0; padding-bottom: 6px; margin-bottom: 12px; }");
        html.append("ul { list-style-type: none; padding: 0; margin: 0; }");
        html.append("li { padding: 8px 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; margin-bottom: 8px; font-size: 13px; }");
        html.append(".badge { display: inline-block; padding: 2px 6px; font-size: 11px; font-weight: 600; border-radius: 4px; margin-right: 6px; }");
        html.append(".badge-high { background: #fee2e2; color: #991b1b; }");
        html.append(".badge-med { background: #fef3c7; color: #92400e; }");
        html.append(".badge-low { background: #f1f5f9; color: #475569; }");
        html.append(".footer { background: #f8fafc; padding: 16px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0; }");
        html.append("</style></head><body>");
        html.append("<div class=\"container\">");
        html.append("<div class=\"header\"><h1>Daily Team Briefing</h1><p>").append(briefing.getDate()).append("</p></div>");
        html.append("<div class=\"content\">");
        html.append("<div class=\"greeting\">Hi ").append(name).append(",</div>");
        html.append("<div class=\"headline\">").append(briefing.getHeadline()).append("</div>");

        appendHtmlSection(html, "Overdue Issues", briefing.getOverdue(), zone);
        appendHtmlSection(html, "Due Today", briefing.getDueToday(), zone);
        appendHtmlSection(html, "Due Soon", briefing.getDueSoon(), zone);
        appendHtmlSection(html, "New Issues (Last 24h)", briefing.getNewIssues(), zone);
        appendHtmlSection(html, "Completed Yesterday", briefing.getCompletedYesterday(), zone);
        appendHtmlMeetings(html, briefing.getUpcomingMeetings(), zone);
        appendHtmlPolls(html, briefing.getActivePolls(), zone);
        appendHtmlInvites(html, briefing.getPendingInvites());
        appendHtmlSprints(html, briefing.getAssignedSprints());
        appendHtmlNotifications(html, briefing.getRecentNotifications(), zone);
        appendHtmlUpdates(html, briefing.getProjectUpdates());
        appendHtmlAlerts(html, briefing.getProjectAlerts());

        html.append("<div style=\"margin-top: 24px; padding-top: 16px; border-top: 1px solid #e2e8f0; font-size: 13px; color: #475569;\">");
        html.append("<strong>Workspace Summary:</strong> Open Issues: ").append(briefing.getOpenIssues());
        if (briefing.getUnreadNotifications() != null) {
            html.append(" | Unread Notifications: ").append(briefing.getUnreadNotifications());
        }
        html.append("</div>");

        html.append("</div>");
        html.append("<div class=\"footer\">FrietSync Team Collaboration &bull; Automated Daily Briefing</div>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private void appendHtmlSection(StringBuilder html, String title, List<IssueSummaryResponse> issues, ZoneId zone) {
        if (issues == null || issues.isEmpty()) return;
        html.append("<div class=\"section\"><h3>").append(title).append(" (").append(issues.size()).append(")</h3><ul>");
        for (IssueSummaryResponse issue : issues) {
            html.append("<li><strong>#").append(issue.getIssueNumber()).append("</strong> ").append(issue.getTitle());
            if (issue.getDueDate() != null) {
                html.append(" <span style=\"float: right; color: #64748b;\">Due: ").append(issue.getDueDate()).append("</span>");
            }
            html.append("</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlMeetings(StringBuilder html, List<com.frietsync.backend.dto.briefing.BriefingMeetingResponse> meetings, ZoneId zone) {
        if (meetings == null || meetings.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Upcoming Meetings (").append(meetings.size()).append(")</h3><ul>");
        for (var m : meetings) {
            html.append("<li><strong>").append(m.getTitle()).append("</strong>");
            if (m.getStartAt() != null) {
                html.append(" <span style=\"float: right; color: #64748b;\">").append(ZonedDateTime.ofInstant(m.getStartAt(), zone).format(TIME)).append("</span>");
            }
            if (m.getAgenda() != null && !m.getAgenda().isBlank()) {
                html.append("<br><small style=\"color: #64748b;\">Agenda: ").append(m.getAgenda()).append("</small>");
            }
            html.append("</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlPolls(StringBuilder html, List<com.frietsync.backend.dto.briefing.BriefingPollSummaryResponse> polls, ZoneId zone) {
        if (polls == null || polls.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Active Polls (").append(polls.size()).append(")</h3><ul>");
        for (var p : polls) {
            html.append("<li><strong>Poll:</strong> ").append(p.getQuestion());
            if (p.getEndsAt() != null) {
                html.append(" <span style=\"float: right; color: #64748b;\">Ends: ").append(ZonedDateTime.ofInstant(p.getEndsAt(), zone).toLocalDate()).append("</span>");
            }
            html.append("</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlInvites(StringBuilder html, List<com.frietsync.backend.dto.briefing.BriefingInviteSummaryResponse> invites) {
        if (invites == null || invites.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Pending Invites (").append(invites.size()).append(")</h3><ul>");
        for (var inv : invites) {
            html.append("<li><strong>").append(inv.getEmail()).append("</strong> (Role: ").append(inv.getRole()).append(", Purpose: ").append(inv.getPurpose()).append(")</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlSprints(StringBuilder html, List<com.frietsync.backend.dto.briefing.BriefingSprintSummaryResponse> sprints) {
        if (sprints == null || sprints.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Assigned Sprints (").append(sprints.size()).append(")</h3><ul>");
        for (var s : sprints) {
            html.append("<li><strong>").append(s.getName()).append("</strong> [Status: ").append(s.getStatus()).append("]");
            if (s.getEndDate() != null) {
                html.append(" <span style=\"float: right; color: #64748b;\">Ends: ").append(s.getEndDate()).append("</span>");
            }
            html.append("</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlNotifications(StringBuilder html, List<com.frietsync.backend.dto.briefing.BriefingNotificationSummaryResponse> notifs, ZoneId zone) {
        if (notifs == null || notifs.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Recent Notifications (").append(notifs.size()).append(")</h3><ul>");
        for (var n : notifs) {
            html.append("<li><strong>").append(n.getTitle() != null ? n.getTitle() : "Notification").append("</strong>: ").append(n.getMessage()).append("</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlUpdates(StringBuilder html, List<ActivityResponse> updates) {
        if (updates == null || updates.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Project Updates (Last 24h) (").append(updates.size()).append(")</h3><ul>");
        for (var u : updates) {
            html.append("<li><strong>#").append(u.getIssueNumber()).append(" ").append(u.getIssueTitle()).append("</strong>: ").append(u.getActorName()).append(" ").append(u.getMessage()).append("</li>");
        }
        html.append("</ul></div>");
    }

    private void appendHtmlAlerts(StringBuilder html, List<String> alerts) {
        if (alerts == null || alerts.isEmpty()) return;
        html.append("<div class=\"section\"><h3>Project Alerts</h3><ul>");
        for (String alert : alerts) {
            html.append("<li style=\"border-left: 4px solid #f59e0b; background: #fffbeb;\">").append(alert).append("</li>");
        }
        html.append("</ul></div>");
    }
}