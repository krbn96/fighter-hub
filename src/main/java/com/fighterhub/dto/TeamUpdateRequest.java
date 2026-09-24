package com.fighterhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import org.openapitools.jackson.nullable.JsonNullable;

public record TeamUpdateRequest(
    @NotBlank
    @Size(max = 255)
    JsonNullable<String> name,

    @Size(max = 30)
    JsonNullable<String> rankRequirement,

    // null(present)は「条件なしへ更新」、[]は「空配列として保存」、
    // 未指定(undefined)は「変更しない」を表す。リスト自体には@NotNullを付けない。
    JsonNullable<List<@NotNull @Positive Long>> characterRequirements,

    JsonNullable<String> recruitmentMessage
) {
    public TeamUpdateRequest {
        name = normalize(name);
        rankRequirement = normalize(rankRequirement);
        characterRequirements = normalize(characterRequirements);
        recruitmentMessage = normalize(recruitmentMessage);
    }

    private static <T> JsonNullable<T> normalize(JsonNullable<T> value) {
        return value == null ? JsonNullable.undefined() : value;
    }
}
