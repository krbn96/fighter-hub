package com.fighterhub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeamCreateResponse(
    Long id,
    Long tournamentId,
    Long ownerId,
    String name,
    String rankRequirement,
    List<Long> characterRequirements,
    String recruitmentMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
