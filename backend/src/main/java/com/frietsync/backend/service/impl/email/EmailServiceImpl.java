package com.frietsync.backend.service.impl.email;

import com.frietsync.backend.dto.email.EmailDetails;
import com.frietsync.backend.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;

    @Override
    public void sendSimpleMail(EmailDetails details) {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(details.getRecipient());
            helper.setSubject(details.getSubject());
            helper.setText(details.getMsgBody(), formatHtml(details.getSubject(), details.getMsgBody()));
            javaMailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Could not prepare email message", exception);
        }
    }

    @Override
    public void sendOtpMail(String recipient, String otp) {
        EmailDetails details = new EmailDetails();
        details.setRecipient(recipient);
        details.setSubject("Your FrietSync verification code");
        details.setMsgBody("Your OTP is: " + otp + "\nThis code expires in 10 minutes.");
        sendSimpleMail(details);
    }

    @Override
    @Async
    public void sendInviteMail(String recipient, String role) {
        EmailDetails details = new EmailDetails();
        details.setRecipient(recipient);
        details.setSubject("You're invited to FrietSync");
        details.setMsgBody("You have been invited to join FrietSync as a " + role + ".");
        sendSimpleMail(details);
    }

    private String formatHtml(String subject, String body) {
        String safeSubject = escapeHtml(subject);
        String formattedBody = escapeHtml(body).replace("\r\n", "\n").replace("\n", "<br>");
        return """
                <!doctype html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:32px 12px;background-color:#f3f5f8;font-family:Arial,Helvetica,sans-serif;color:#202735;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;background:#ffffff;border:1px solid #e4e8ef;border-radius:12px;">
                    <tr><td style="padding:24px 32px;background:#173b35;border-radius:12px 12px 0 0;color:#ffffff;font-size:20px;font-weight:bold;">FrietSync</td></tr>
                    <tr><td style="padding:32px;">
                      <h1 style="margin:0 0 20px;font-size:22px;line-height:1.35;color:#202735;">%s</h1>
                      <p style="margin:0;font-size:15px;line-height:1.7;color:#465064;">%s</p>
                    </td></tr>
                    <tr><td style="padding:18px 32px;border-top:1px solid #e9edf2;color:#7a8494;font-size:12px;line-height:1.5;">This is an automated message from FrietSync. Please do not reply to this email.</td></tr>
                  </table>
                </body>
                </html>
                """.formatted(safeSubject, formattedBody);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}