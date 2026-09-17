package com.fighterhub.dto;

public record UserCharacterResponse(
    Long characterId,
    String rank,
    Integer mr
) {
}
