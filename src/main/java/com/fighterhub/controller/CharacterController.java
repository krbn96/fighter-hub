package com.fighterhub.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.CharacterResponse;
import com.fighterhub.service.CharacterService;
import com.fighterhub.dto.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Operation(
        summary = "キャラクター一覧取得",
        description = "登録されているキャラクターを一覧で取得します。"
    )
    @ApiResponse(
        responseCode = "200",
        description = "取得成功"
    )
    @GetMapping
    public List<CharacterResponse> findAll() {
        return characterService.findAll();
    }

    @Operation(
        summary = "キャラクター詳細取得",
        description = "指定したIDのキャラクターを取得します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "IDの形式が不正",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "指定したキャラクターが存在しない",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/{id}")
    public CharacterResponse findById(@PathVariable Long id) {
        return characterService.findById(id);
    }
}