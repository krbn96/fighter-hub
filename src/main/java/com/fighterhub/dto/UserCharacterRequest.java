package com.fighterhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCharacterRequest(
    @NotNull
    Long characterId,

    @NotBlank
    String rank,

    Integer mr
) {
}
