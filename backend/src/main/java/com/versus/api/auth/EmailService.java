package com.versus.api.auth;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${versus.mail.from}")
    private String from;

    @Value("${versus.mail.from-name}")
    private String fromName;

    @Value("${versus.frontend.base-url}")
    private String frontendBaseUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String verifyUrl = frontendBaseUrl + "/verify-email?token=" + token;
        Context ctx = new Context(Locale.forLanguageTag("es"));
        ctx.setVariables(Map.of(
                "username", username,
                "verifyUrl", verifyUrl,
                "frontendBaseUrl", frontendBaseUrl
        ));
        send(toEmail, "Verifica tu cuenta en Versus", "email/verification", ctx);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;
        Context ctx = new Context(Locale.forLanguageTag("es"));
        ctx.setVariables(Map.of(
                "username", username,
                "resetUrl", resetUrl,
                "frontendBaseUrl", frontendBaseUrl
        ));
        send(toEmail, "Recupera tu contraseña en Versus", "email/password-reset", ctx);
    }

    private void send(String to, String subject, String template, Context ctx) {
        try {
            String html = templateEngine.process(template, ctx);
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}
