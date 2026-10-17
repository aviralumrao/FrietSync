package com.frietsync.backend.service.impl.activity;

import com.frietsync.backend.dto.activity.DelayedIssueResponse;
import com.frietsync.backend.dto.activity.ProjectSummaryResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Component
public class SummaryPdfBuilder {

    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private final Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private final Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private final Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

    public byte[] build(ProjectSummaryResponse summary) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Project Summary", titleFont));
            document.add(new Paragraph(summary.getProjectName(), headingFont));
            document.add(new Paragraph("Generated at: " + summary.getGeneratedAt(), normalFont));

            document.add(heading("Project"));
            PdfPTable project = twoColumnTable();
            row(project, "Status", String.valueOf(summary.getProjectStatus()));
            row(project, "Deadline", String.valueOf(summary.getDeadline()));
            if (summary.isProjectDelayed()) {
                row(project, "Delayed by", summary.getProjectDaysLate() + " days");
            } else {
                row(project, "Delayed", "No");
            }
            document.add(project);

            document.add(heading("Issues"));
            PdfPTable issues = twoColumnTable();
            row(issues, "Total", String.valueOf(summary.getTotalIssues()));
            row(issues, "Open", String.valueOf(summary.getOpenIssues()));
            row(issues, "In progress", String.valueOf(summary.getInProgressIssues()));
            row(issues, "Resolved", String.valueOf(summary.getResolvedIssues()));
            row(issues, "Reopened", String.valueOf(summary.getReopenedIssues()));
            row(issues, "Closed", String.valueOf(summary.getClosedIssues()));
            row(issues, "Overdue", String.valueOf(summary.getOverdueIssues()));
            row(issues, "Completed late", String.valueOf(summary.getCompletedLateIssues()));
            document.add(issues);

            document.add(heading("Estimated vs actual hours"));
            PdfPTable hours = twoColumnTable();
            row(hours, "Total estimated (all issues)", String.valueOf(summary.getTotalEstimatedHours()));
            row(hours, "Estimated (finished issues)", String.valueOf(summary.getFinishedEstimatedHours()));
            row(hours, "Actual (finished issues)", String.valueOf(summary.getFinishedActualHours()));
            document.add(hours);

            document.add(heading("Sprints"));
            PdfPTable sprints = twoColumnTable();
            row(sprints, "Total", String.valueOf(summary.getTotalSprints()));
            row(sprints, "Planned", String.valueOf(summary.getPlannedSprints()));
            row(sprints, "Active", String.valueOf(summary.getActiveSprints()));
            row(sprints, "Completed", String.valueOf(summary.getCompletedSprints()));
            row(sprints, "Overdue", String.valueOf(summary.getOverdueSprints()));
            document.add(sprints);

            document.add(heading("Delayed issues"));
            if (summary.getDelayedIssues().isEmpty()) {
                document.add(new Paragraph("No delayed issues.", normalFont));
            } else {
                PdfPTable delayed = new PdfPTable(4);
                delayed.setWidthPercentage(100);
                delayed.setSpacingBefore(6);
                delayed.addCell(new Phrase("Issue", boldFont));
                delayed.addCell(new Phrase("State", boldFont));
                delayed.addCell(new Phrase("Due date", boldFont));
                delayed.addCell(new Phrase("Days late", boldFont));
                for (DelayedIssueResponse issue : summary.getDelayedIssues()) {
                    delayed.addCell(new Phrase("#" + issue.getIssueNumber() + " " + issue.getTitle(), normalFont));
                    delayed.addCell(new Phrase(issue.getState(), normalFont));
                    delayed.addCell(new Phrase(String.valueOf(issue.getDueDate()), normalFont));
                    delayed.addCell(new Phrase(String.valueOf(issue.getDaysLate()), normalFont));
                }
                document.add(delayed);
            }

            document.close();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate the summary PDF", ex);
        }
        return out.toByteArray();
    }

    private Paragraph heading(String text) {
        Paragraph paragraph = new Paragraph(text, headingFont);
        paragraph.setSpacingBefore(14);
        return paragraph;
    }

    private PdfPTable twoColumnTable() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        return table;
    }

    private void row(PdfPTable table, String label, String value) {
        table.addCell(new Phrase(label, boldFont));
        table.addCell(new Phrase(value, normalFont));
    }
}