package com.fighterhub.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.service.TournamentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @Operation(
        summary = "大会一覧取得",
        description = "有効な大会の一覧を取得します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping
    public List<TournamentResponse> findAllTournaments() {
        return tournamentService.findAllTournaments();
    }

    @Operation(
        summary = "大会詳細取得",
        description = "指定したIDの大会を取得します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功",
            content = @Content(
                schema = @Schema(implementation = TournamentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "指定した大会が存在しない",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/{id}")
    public TournamentResponse findTournamentById(@PathVariable Long id) {
        return tournamentService.findTournamentById(id);
    }
}
