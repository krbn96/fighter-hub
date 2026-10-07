package com.fighterhub.controller;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.dto.TeamResponse;
import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.dto.TournamentUpdateRequest;
import com.fighterhub.service.TeamService;
import com.fighterhub.service.TournamentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;
    private final TeamService teamService;

    public TournamentController(TournamentService tournamentService, TeamService teamService) {
        this.tournamentService = tournamentService;
        this.teamService = teamService;
    }

    @Operation(
        summary = "大会一覧取得",
        description = "有効な大会の一覧を取得します。"
            + "nameを指定すると大会名の部分一致(大文字小文字非区別)で絞り込みます。"
            + "recruitingを指定すると募集中(true)/募集終了(false)で絞り込みます。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "recruitingがBooleanとして解釈できない",
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
    @GetMapping
    public List<TournamentResponse> findAllTournaments(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean recruiting) {
        return tournamentService.findAllTournaments(name, recruiting);
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

    @Operation(
        summary = "大会ごとのチーム一覧取得",
        description = "指定した大会に属する有効なチームの一覧を取得します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功"
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
    @GetMapping("/{tournamentId}/teams")
    public List<TeamResponse> findTeamsByTournament(@PathVariable Long tournamentId) {
        return teamService.findTeamsByTournament(tournamentId);
    }

    @Operation(
        summary = "大会作成",
        description = "ADMINユーザーが新しい大会を作成します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "作成成功",
            content = @Content(
                schema = @Schema(implementation = TournamentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "リクエストが不正、またはBean Validationエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "ADMINではないユーザーによる作成",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "JWTのsubに対応するユーザーが存在しない",
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
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public TournamentResponse createTournament(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TournamentCreateRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return tournamentService.createTournament(userId, request);
    }

    @Operation(
        summary = "大会更新",
        description = "ADMINユーザーが大会情報を部分更新します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "更新成功",
            content = @Content(
                schema = @Schema(implementation = TournamentResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "リクエストが不正、またはBean Validationエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "ADMINではないユーザーによる更新",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "JWTのsubに対応するユーザー、または指定した大会が存在しない",
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
    @PatchMapping("/{id}")
    public TournamentResponse updateTournament(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TournamentUpdateRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return tournamentService.updateTournament(userId, id, request);
    }

    @Operation(
        summary = "大会削除",
        description = "ADMINユーザーが大会を論理削除します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "削除成功"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "ADMINではないユーザーによる削除",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "JWTのsubに対応するユーザー、または指定した大会が存在しない",
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void deleteTournament(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long userId = Long.valueOf(jwt.getSubject());
        tournamentService.deleteTournament(userId, id);
    }
}
