package com.versus.api.email;

import com.versus.api.email.config.EmailProperties;
import com.versus.api.email.model.EmailRequest;
import com.versus.api.email.template.EmailTemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final EmailProperties properties;

    public void send(String to, String subject, String template, Map<String, Object> variables) {
        send(EmailRequest.fromTemplate(to, subject, template, variables));
    }

    public void sendHtml(String to, String subject, String htmlBody) {
        send(EmailRequest.fromHtml(to, subject, htmlBody));
    }

    public void send(EmailRequest request) {
        validate(request);
        if (!properties.enabled()) {
            log.warn("Email delivery skipped because the infrastructure email module is disabled. to={}", request.to());
            return;
        }

        String html = StringUtils.hasText(request.htmlBody())
                ? request.htmlBody()
                : templateRenderer.render(request.template(), request.variables());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(request.to());
            helper.setSubject(request.subject());
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent successfully to {}", request.to());
        } catch (MessagingException | MailException ex) {
            log.error("Email delivery failed to {}: {}", request.to(), ex.getMessage(), ex);
            throw new EmailSendingException("Email delivery failed", ex);
        } catch (Exception ex) {
            log.error("Unexpected email delivery failure to {}: {}", request.to(), ex.getMessage(), ex);
            throw new EmailSendingException("Unexpected email delivery failure", ex);
        }
    }

    private void validate(EmailRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Email request is required");
        }
        if (!StringUtils.hasText(request.to())) {
            throw new IllegalArgumentException("Email recipient is required");
        }
        if (!StringUtils.hasText(request.subject())) {
            throw new IllegalArgumentException("Email subject is required");
        }
        if (!StringUtils.hasText(request.htmlBody()) && !StringUtils.hasText(request.template())) {
            throw new IllegalArgumentException("Email template or HTML body is required");
        }
    }
}
