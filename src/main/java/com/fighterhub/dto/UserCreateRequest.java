package com.fighterhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public record UserCreateRequest(
    @NotBlank
    @Email
    String email,

    @NotBlank
    @Size(min = 8, max = 100)
    String password,

    @NotBlank
    @Size(max = 50)
    String name,

    @NotNull
    @Size(min = 1, max = 4)
    List<@Valid UserCharacterRequest> characters,

    LocalTime playTimeStart,

    LocalTime playTimeEnd,

    String message
) {
}
