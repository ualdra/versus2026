package com.versus.api.email.template;

import lombok.RequiredArgsConstructor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
public class ThymeleafEmailTemplateRenderer implements EmailTemplateRenderer {

    private final TemplateEngine templateEngine;

    @Override
    public String render(String template, Map<String, Object> variables) {
        Context context = new Context(Locale.getDefault());
        if (variables != null) {
            context.setVariables(variables);
        }
        return templateEngine.process(template, context);
    }
}
