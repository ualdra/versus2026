package com.versus.api.email.model;

import java.util.Map;

public record EmailRequest(
        String to,
        String subject,
        String template,
        String htmlBody,
        Map<String, Object> variables
) {
    public static EmailRequest fromTemplate(
            String to,
            String subject,
            String template,
            Map<String, Object> variables
    ) {
        return new EmailRequest(to, subject, template, null, variables);
    }

    public static EmailRequest fromHtml(String to, String subject, String htmlBody) {
        return new EmailRequest(to, subject, null, htmlBody, Map.of());
    }
}
