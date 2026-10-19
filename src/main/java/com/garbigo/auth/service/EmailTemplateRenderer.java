package com.garbigo.auth.service;

import com.garbigo.auth.util.HtmlToText;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

@Component
public class EmailTemplateRenderer {

    public record Rendered(String html, String text) {
    }

    private final SpringTemplateEngine templateEngine;

    public EmailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public Rendered render(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(variables);
        String html = templateEngine.process(templateName, context);
        return new Rendered(html, HtmlToText.convert(html));
    }
}
