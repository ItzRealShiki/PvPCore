package de.leon.aiadmin.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class LogReader {
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern API_SECRET = Pattern.compile("(?i)\\bsk-[A-Za-z0-9_-]{16,}");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile("(?i)(api[-_ ]?key|access[-_ ]?token|refresh[-_ ]?token|password|passwd|secret)\\s*[:=]\\s*[^\\s,;]+(?=$|[\\s,;])");
    private static final Pattern CHAT_LINE = Pattern.compile("(?m)^([^\\r\\n]*\\[(?:Server thread|Async Chat Thread[^]]*)/INFO\\]:\\s*)<[^>]+>.*$");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~-]{16,}");

    private LogReader() {}

    public static String readRecentLogs(Path serverRoot, int maxChars) {
        Path latest = serverRoot.resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(latest)) return "No logs/latest.log file was found.";
        try {
            String text = Files.readString(latest, StandardCharsets.UTF_8);
            if (text.length() > maxChars) text = text.substring(text.length() - maxChars);
            return sanitize(text);
        } catch (IOException exception) {
            return "Could not read latest.log: " + exception.getClass().getSimpleName();
        }
    }

    public static String readLatestCrashReport(Path serverRoot, int maxChars) {
        Path folder = serverRoot.resolve("crash-reports");
        if (!Files.isDirectory(folder)) return "";
        try (Stream<Path> files = Files.list(folder)) {
            Path latest = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparing(LogReader::lastModified))
                    .orElse(null);
            if (latest == null) return "";
            String text = Files.readString(latest, StandardCharsets.UTF_8);
            if (text.length() > maxChars) text = text.substring(text.length() - maxChars);
            return "File: " + latest.getFileName() + "\n" + sanitize(text);
        } catch (IOException exception) {
            return "Could not read crash report: " + exception.getClass().getSimpleName();
        }
    }

    public static String sanitize(String input) {
        String value = ANSI.matcher(input).replaceAll("");
        value = API_SECRET.matcher(value).replaceAll("[REDACTED_API_KEY]");
        value = BEARER.matcher(value).replaceAll("Bearer [REDACTED_TOKEN]");
        value = KEY_VALUE_SECRET.matcher(value).replaceAll("$1=[REDACTED]");
        value = IPV4.matcher(value).replaceAll("[REDACTED_IP]");
        value = CHAT_LINE.matcher(value).replaceAll("$1<PLAYER> [chat line redacted]");
        return value;
    }

    private static FileTime lastModified(Path path) {
        try { return Files.getLastModifiedTime(path); }
        catch (IOException ignored) { return FileTime.fromMillis(0); }
    }
}
