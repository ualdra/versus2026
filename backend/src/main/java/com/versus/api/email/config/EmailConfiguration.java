package com.versus.api.email.config;

import com.versus.api.email.EmailService;
import com.versus.api.email.template.ThymeleafEmailTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

@Slf4j
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {

    @Bean(name = "infrastructureEmailService")
    public EmailService infrastructureEmailService(
            JavaMailSender mailSender,
            EmailProperties emailProperties,
            Environment environment,
            TemplateEngine templateEngine
    ) {
        if (!emailProperties.enabled()) {
            log.warn("Infrastructure email module is disabled. Set VERSUS_EMAIL_ENABLED=true to send emails.");
        }
        String smtpHost = environment.getProperty("spring.mail.host");
        if (smtpHost == null || smtpHost.isBlank()) {
            log.warn("SMTP host is not configured. Set SMTP_HOST before enabling email delivery.");
        }
        return new EmailService(mailSender, new ThymeleafEmailTemplateRenderer(templateEngine), emailProperties);
    }
}
