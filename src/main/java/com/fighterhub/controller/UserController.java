package com.fighterhub.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
        summary = "ユーザー登録",
        description = "新しいユーザーを登録します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "登録成功",
            content = @Content(
                schema = @Schema(implementation = UserCreateResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "リクエストが不正",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "emailが既に登録されている",
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
    public UserCreateResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        return userService.createUser(request);
    }
}
