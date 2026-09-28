package com.fighterhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record TournamentCreateRequest(
    @NotBlank
    String name,

    @NotNull
    @Positive
    Integer teamSize,

    @NotNull
    LocalDateTime startAt,

    @NotNull
    @Positive
    Integer maxPlayers,

    @NotBlank
    String status
) {
}
