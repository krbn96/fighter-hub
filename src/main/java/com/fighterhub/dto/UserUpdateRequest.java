package com.fighterhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

import org.openapitools.jackson.nullable.JsonNullable;

public record UserUpdateRequest(
    @NotBlank
    @Size(max = 50)
    JsonNullable<String> name,

    @NotNull
    @Size(min = 1, max = 4)
    JsonNullable<List<@Valid UserCharacterRequest>> characters,

    JsonNullable<LocalTime> playTimeStart,

    JsonNullable<LocalTime> playTimeEnd,

    JsonNullable<String> message
) {
    public UserUpdateRequest {
        name = normalize(name);
        characters = normalize(characters);
        playTimeStart = normalize(playTimeStart);
        playTimeEnd = normalize(playTimeEnd);
        message = normalize(message);
    }

    private static <T> JsonNullable<T> normalize(JsonNullable<T> value) {
        return value == null ? JsonNullable.undefined() : value;
    }
}
