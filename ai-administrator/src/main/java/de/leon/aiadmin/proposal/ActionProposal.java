package de.leon.aiadmin.proposal;

public record ActionProposal(
        String id,
        String type,
        String title,
        String target,
        String value,
        String reason,
        String risk,
        boolean requiresRestart,
        String status,
        String createdAt
) {}
