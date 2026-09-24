package com.fighterhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TeamCreateRequest(
    @NotNull
    Long tournamentId,

    @NotBlank
    @Size(max = 255)
    String name,

    @Size(max = 30)
    String rankRequirement,

    // null/空リストはいずれも「キャラクター条件なし」を意味するため、
    // リスト自体には@NotNullを付けない。要素はnull不可・正のIDのみ許可する。
    List<@NotNull @Positive Long> characterRequirements,

    String recruitmentMessage
) {
}
