package de.leon.aiadmin.ai;

import java.util.List;

public record AIAnalysis(
        String severity,
        String summary,
        String rootCause,
        List<Finding> findings,
        List<ProposedActionData> actions
) {
    public record Finding(String title, String evidence, String explanation, String recommendation) {}
    public record ProposedActionData(String type, String title, String target, String value,
                                     String reason, String risk, boolean requiresRestart) {}
}
