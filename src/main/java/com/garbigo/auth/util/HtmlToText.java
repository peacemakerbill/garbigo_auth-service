package com.garbigo.auth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlToText {

    private static final Pattern HEAD = Pattern.compile("(?is)<head\\b.*?</head>");
    private static final Pattern STYLE_OR_SCRIPT = Pattern.compile("(?is)<(style|script)\\b.*?</\\1>");
    private static final Pattern HIDDEN_BLOCK = Pattern.compile("(?is)<!--\\s*no-text-start\\s*-->.*?<!--\\s*no-text-end\\s*-->");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\b[^>]*?\\bhref\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a>");
    private static final Pattern BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|h1|h2|h3|h4|div|tr|li|ul|ol|table)>");
    private static final Pattern LABEL_END = Pattern.compile("(?i)</th>");
    private static final Pattern CELL_END = Pattern.compile("(?i)</td>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(\\d{1,6});");
    private static final Pattern HEX_ENTITY = Pattern.compile("(?i)&#x([0-9a-f]{1,6});");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00a0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlToText() {
    }

    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String text = html;
        text = HEAD.matcher(text).replaceAll("");
        text = STYLE_OR_SCRIPT.matcher(text).replaceAll("");
        text = HIDDEN_BLOCK.matcher(text).replaceAll("");
        text = COMMENT.matcher(text).replaceAll("");

        text = replaceLinks(text);
        text = BREAK.matcher(text).replaceAll("\n");
        text = LABEL_END.matcher(text).replaceAll(": ");
        text = CELL_END.matcher(text).replaceAll(" ");
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = decodeEntities(text);

        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\r?\\n", -1)) {
            String cleaned = SPACES.matcher(line).replaceAll(" ").trim();
            out.append(cleaned).append('\n');
        }

        String result = BLANK_LINES.matcher(out).replaceAll("\n\n").trim();
        return result + "\n";
    }

    private static String replaceLinks(String html) {
        Matcher m = LINK.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String url = decodeEntities(m.group(1)).trim();
            String label = decodeEntities(TAG.matcher(m.group(2)).replaceAll("")).trim();
            String replacement;
            if (url.isEmpty()) {
                replacement = label;
            } else if (label.isEmpty() || label.equals(url)) {
                replacement = url;
            } else {
                replacement = label + ": " + url;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String decodeEntities(String value) {
        String text = value;
        Matcher hex = HEX_ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (hex.find()) {
            hex.appendReplacement(sb, Matcher.quoteReplacement(codePoint(Integer.parseInt(hex.group(1), 16))));
        }
        hex.appendTail(sb);
        text = sb.toString();

        Matcher num = NUMERIC_ENTITY.matcher(text);
        sb = new StringBuilder();
        while (num.find()) {
            num.appendReplacement(sb, Matcher.quoteReplacement(codePoint(Integer.parseInt(num.group(1)))));
        }
        num.appendTail(sb);
        text = sb.toString();

        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&copy;", "(c)")
                .replace("&amp;", "&");
    }

    private static String codePoint(int value) {
        if (value <= 0 || value > 0x10FFFF) {
            return "";
        }
        return new String(Character.toChars(value));
    }
}
