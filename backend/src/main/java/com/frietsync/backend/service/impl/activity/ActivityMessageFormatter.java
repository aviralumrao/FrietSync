package com.frietsync.backend.service.impl.activity;

import com.frietsync.backend.entity.activity.ProjectActivity;
import com.frietsync.backend.entity.issue.IssueHistory;
import com.frietsync.backend.entity.issue.IssueHistoryAction;

public final class ActivityMessageFormatter {

    private static final int MAX_MESSAGE_LENGTH = 240;

    private ActivityMessageFormatter() {
    }

    public static String forProjectActivity(ProjectActivity activity, String actorName) {
        String actor = actor(actorName);
        String type = activity.getResourceType() == null
                ? "project item"
                : activity.getResourceType().toLowerCase().replace('_', ' ');
        String resource = activity.getResourceName() == null || activity.getResourceName().isBlank()
                ? type
                : type + " \"" + activity.getResourceName() + "\"";
        String action = switch (activity.getAction()) {
            case "CREATED" -> "created";
            case "COMPLETED" -> "completed";
            default -> "updated";
        };
        return limit(actor + " " + action + " " + resource + ".");
    }

    public static String forIssue(IssueHistory entry, int issueNumber, String issueTitle, String actorName) {
        String actor = actor(actorName);
        String issue = issueNumber > 0 ? "issue #" + issueNumber : "com/frietsync/backend/config/issue";
        if (issueTitle != null && !issueTitle.isBlank()) {
            issue += ": " + issueTitle;
        }

        IssueHistoryAction action = entry.getAction();
        if (action == IssueHistoryAction.UPDATED) {
            String field = entry.getFieldName();
            String detail = field == null || field.isBlank() ? "updated " : "updated " + field + " on ";
            String message = actor + " " + detail + issue;
            if (entry.getOldValue() != null || entry.getNewValue() != null) {
                message += " (" + value(entry.getOldValue()) + " → " + value(entry.getNewValue()) + ")";
            }
            return limit(message + ".");
        }

        String message = switch (action) {
            case CREATED -> actor + " created " + issue;
            case ASSIGNED -> actor + " assigned contributors to " + issue;
            case ASSIGNEE_REMOVED -> actor + " removed an assignee from " + issue;
            case ASSIGNMENT_REQUESTED -> actor + " requested assignment to " + issue;
            case ASSIGNMENT_APPROVED -> actor + " approved an assignment for " + issue;
            case ASSIGNMENT_REJECTED -> actor + " rejected an assignment for " + issue;
            case RESOLVED -> actor + " resolved " + issue;
            case REVIEW_APPROVED -> actor + " approved the review for " + issue;
            case REVIEW_REJECTED -> actor + " rejected the review for " + issue;
            case LABEL_ADDED -> actor + " added a label to " + issue;
            case LABEL_REMOVED -> actor + " removed a label from " + issue;
            case ATTACHMENT_ADDED -> actor + " added an attachment to " + issue;
            case ATTACHMENT_REMOVED -> actor + " removed an attachment from " + issue;
            case LINK_ADDED -> actor + " linked " + issue;
            case LINK_REMOVED -> actor + " removed a link from " + issue;
            case DELETED -> actor + " deleted " + issue;
            case RESTORED -> actor + " restored " + issue;
            case UPDATED -> actor + " updated " + issue;
        };
        return limit(message + ".");
    }

    private static String actor(String name) {
        return name == null || name.isBlank() ? "Someone" : name;
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "empty" : value;
    }

    private static String limit(String message) {
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH - 1) + "…";
    }
}
