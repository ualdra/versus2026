package com.versus.api.email.template;

import java.util.Map;

public interface EmailTemplateRenderer {
    String render(String template, Map<String, Object> variables);
}
