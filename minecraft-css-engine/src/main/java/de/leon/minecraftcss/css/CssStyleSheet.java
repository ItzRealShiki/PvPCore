package de.leon.minecraftcss.css;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CssStyleSheet {
    public record Rule(String selector, Map<String, String> declarations) {}

    private final List<Rule> rules;

    private CssStyleSheet(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static CssStyleSheet parse(String source) {
        String cleaned = stripComments(source);
        List<Rule> parsed = new ArrayList<>();
        int cursor = 0;

        while (cursor < cleaned.length()) {
            int open = findOutsideQuotes(cleaned, '{', cursor);
            if (open < 0) break;
            int close = findOutsideQuotes(cleaned, '}', open + 1);
            if (close < 0) {
                throw new IllegalArgumentException("Missing closing '}' in stylesheet.");
            }

            String selectorText = cleaned.substring(cursor, open).trim();
            String body = cleaned.substring(open + 1, close);
            Map<String, String> declarations = parseDeclarations(body);

            for (String selector : splitOutsideQuotes(selectorText, ',')) {
                String normalized = selector.trim();
                if (!normalized.isEmpty() && !declarations.isEmpty()) {
                    parsed.add(new Rule(normalized, Map.copyOf(declarations)));
                }
            }
            cursor = close + 1;
        }
        return new CssStyleSheet(parsed);
    }

    public Map<String, String> stylesFor(String selector) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (rule.selector().equalsIgnoreCase(selector)) {
                result.putAll(rule.declarations());
            }
        }
        return result;
    }

    public List<Rule> rules() {
        return rules;
    }

    private static Map<String, String> parseDeclarations(String body) {
        Map<String, String> declarations = new LinkedHashMap<>();
        for (String part : splitOutsideQuotes(body, ';')) {
            int colon = findOutsideQuotes(part, ':', 0);
            if (colon <= 0) continue;
            String property = part.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(colon + 1).trim();
            if (!property.isEmpty() && !value.isEmpty()) {
                declarations.put(property, value);
            }
        }
        return declarations;
    }

    private static List<String> splitOutsideQuotes(String input, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && quote != 0) {
                current.append(c);
                escaped = true;
                continue;
            }
            if ((c == '"' || c == '\'') && (quote == 0 || quote == c)) {
                quote = quote == 0 ? c : 0;
                current.append(c);
                continue;
            }
            if (c == delimiter && quote == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static int findOutsideQuotes(String input, char target, int start) {
        char quote = 0;
        boolean escaped = false;
        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && quote != 0) {
                escaped = true;
                continue;
            }
            if ((c == '"' || c == '\'') && (quote == 0 || quote == c)) {
                quote = quote == 0 ? c : 0;
                continue;
            }
            if (c == target && quote == 0) return i;
        }
        return -1;
    }

    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && quote != 0) {
                result.append(c);
                escaped = true;
                continue;
            }
            if ((c == '"' || c == '\'') && (quote == 0 || quote == c)) {
                quote = quote == 0 ? c : 0;
                result.append(c);
                continue;
            }
            if (quote == 0 && c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed CSS comment.");
                i = end + 1;
                result.append(' ');
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }
}
