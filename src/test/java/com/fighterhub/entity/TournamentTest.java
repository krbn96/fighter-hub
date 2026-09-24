package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TournamentTest {

    @Test
    void create_指定した値でTournamentを生成できる() {
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 1, 19, 0);

        Tournament tournament = Tournament.create(
                "STREET FIGHTER 6 CUP",
                3,
                startAt,
                64,
                "OPEN"
        );

        assertEquals("STREET FIGHTER 6 CUP", tournament.getName());
        assertEquals(3, tournament.getTeamSize());
        assertEquals(startAt, tournament.getStartAt());
        assertEquals(64, tournament.getMaxPlayers());
        assertEquals("OPEN", tournament.getStatus());
    }

    @Test
    void create_deleteFlagの初期値はfalseである() {
        Tournament tournament = Tournament.create(
                "Test Cup", 3, LocalDateTime.now(), 8, "OPEN");

        assertFalse(tournament.isDeleteFlag());
    }
}
