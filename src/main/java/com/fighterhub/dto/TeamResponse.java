package com.fighterhub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeamResponse(
    Long id,
    Long tournamentId,
    String tournamentName,
    Long ownerId,
    String ownerName,
    String name,
    String rankRequirement,
    List<Long> characterRequirements,
    String recruitmentMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
