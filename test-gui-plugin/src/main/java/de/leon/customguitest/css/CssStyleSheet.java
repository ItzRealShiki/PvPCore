package de.leon.customguitest.css;

import java.util.*;
import java.util.regex.*;

public final class CssStyleSheet {
    public record Rule(String selector, Map<String, String> declarations) {}

    private final List<Rule> rules;

    private CssStyleSheet(List<Rule> rules) { this.rules = List.copyOf(rules); }

    public static CssStyleSheet parse(String source) {
        List<Rule> rules = new ArrayList<>();
        Matcher matcher = Pattern.compile("([^{}]+)\\{([^{}]*)}").matcher(stripComments(source));
        while (matcher.find()) {
            String selector = matcher.group(1).trim();
            Map<String, String> declarations = new LinkedHashMap<>();
            for (String declaration : matcher.group(2).split(";")) {
                int colon = declaration.indexOf(':');
                if (colon < 1) continue;
                declarations.put(declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        declaration.substring(colon + 1).trim());
            }
            if (!declarations.isEmpty()) rules.add(new Rule(selector, declarations));
        }
        return new CssStyleSheet(rules);
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    public Map<String, String> stylesFor(String selector) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Rule rule : rules) {
            for (String candidate : rule.selector().split(",")) {
                if (candidate.trim().equalsIgnoreCase(selector)) result.putAll(rule.declarations());
            }
        }
        return result;
    }

    public List<Rule> rules() { return rules; }
}
