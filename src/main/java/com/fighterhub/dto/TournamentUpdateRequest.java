package com.fighterhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

import org.openapitools.jackson.nullable.JsonNullable;

public record TournamentUpdateRequest(
    @NotBlank
    JsonNullable<String> name,

    @NotNull
    @Positive
    JsonNullable<Integer> teamSize,

    @NotNull
    JsonNullable<LocalDateTime> startAt,

    @NotNull
    JsonNullable<LocalDateTime> recruitmentDeadline,

    @NotNull
    @Positive
    JsonNullable<Integer> maxPlayers,

    @NotBlank
    JsonNullable<String> status
) {
    public TournamentUpdateRequest {
        name = normalize(name);
        teamSize = normalize(teamSize);
        startAt = normalize(startAt);
        recruitmentDeadline = normalize(recruitmentDeadline);
        maxPlayers = normalize(maxPlayers);
        status = normalize(status);
    }

    private static <T> JsonNullable<T> normalize(JsonNullable<T> value) {
        return value == null ? JsonNullable.undefined() : value;
    }
}
