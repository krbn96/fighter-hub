package com.fighterhub.dto;

import java.time.LocalDateTime;

public record TournamentResponse(
    Long id,
    String name,
    Integer teamSize,
    LocalDateTime startAt,
    LocalDateTime recruitmentDeadline,
    Integer maxPlayers,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
