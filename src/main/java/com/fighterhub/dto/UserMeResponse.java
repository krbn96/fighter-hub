package com.fighterhub.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record UserMeResponse(
    Long id,
    String name,
    List<UserCharacterResponse> characters,
    LocalTime playTimeStart,
    LocalTime playTimeEnd,
    String message,
    String email,
    String xId,
    String discordId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
