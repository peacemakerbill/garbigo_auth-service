package com.garbigo.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Overrides Spring Boot's auto-configured JavaMailSender to defend against a
 * pasted SMTP username/password that's wrapped in quotes.
 * <p>
 * .env is loaded here as Java .properties (via spring.config.import's
 * [.properties] hint), where a quote character is just literal content -
 * unlike shell or JS dotenv tooling, nothing strips a value pasted as
 * 'my-pass' or "my-pass" down to my-pass. Rather than relying on everyone
 * remembering to paste credentials with no surrounding punctuation, this
 * strips one layer of wrapping quotes (and outer whitespace) from whatever
 * Spring Boot already bound from spring.mail.* before handing it to the
 * actual mail sender - so either version works.
 * <p>
 * MailSenderAutoConfiguration is itself gated by
 * {@code @ConditionalOnMissingBean(MailSender.class)} on the whole class, and
 * JavaMailSender extends MailSender - so defining our own JavaMailSender bean
 * below suppresses that entire auto-configuration class, including the
 * {@code @EnableConfigurationProperties(MailProperties.class)} declaration
 * that would otherwise register the properties bean. @EnableConfigurationProperties
 * here binds it ourselves instead, independent of that suppressed class.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    JavaMailSender javaMailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        sender.setHost(properties.getHost());
        if (properties.getPort() != null) {
            sender.setPort(properties.getPort());
        }
        sender.setProtocol(properties.getProtocol());
        if (properties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(properties.getDefaultEncoding().name());
        }

        sender.setUsername(stripWrappingQuotes(properties.getUsername()));
        sender.setPassword(stripWrappingQuotes(properties.getPassword()));

        Properties javaMailProperties = sender.getJavaMailProperties();
        properties.getProperties().forEach(javaMailProperties::setProperty);

        return sender;
    }

    /**
     * Strips a single layer of wrapping ' or " quotes, and any outer whitespace,
     * from a pasted credential. "'my-pass'", "\"my-pass\"" and "my-pass" all come
     * out as my-pass; anything not wrapped in a matching pair is left untouched
     * (trimmed) so a password that legitimately starts or ends with a quote
     * character isn't mangled.
     */
    private String stripWrappingQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            boolean wrapped = (first == '\'' && last == '\'') || (first == '"' && last == '"');
            if (wrapped) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }
}