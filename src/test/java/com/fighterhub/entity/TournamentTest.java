package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TournamentTest {

    @Test
    void create_指定した値でTournamentを生成できる() {
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 1, 19, 0);
        LocalDateTime recruitmentDeadline = LocalDateTime.of(2026, 9, 30, 23, 59);

        Tournament tournament = Tournament.create(
                "STREET FIGHTER 6 CUP",
                3,
                startAt,
                recruitmentDeadline,
                64,
                "OPEN"
        );

        assertEquals("STREET FIGHTER 6 CUP", tournament.getName());
        assertEquals(3, tournament.getTeamSize());
        assertEquals(startAt, tournament.getStartAt());
        assertEquals(recruitmentDeadline, tournament.getRecruitmentDeadline());
        assertEquals(64, tournament.getMaxPlayers());
        assertEquals("OPEN", tournament.getStatus());
    }

    @Test
    void create_deleteFlagの初期値はfalseである() {
        Tournament tournament = Tournament.create(
                "Test Cup", 3, LocalDateTime.now(), LocalDateTime.now().minusDays(1), 8, "OPEN");

        assertFalse(tournament.isDeleteFlag());
    }

    // 正式business rule: now < recruitmentDeadline → RECRUITING(true)、
    // now >= recruitmentDeadline(締切ちょうど含む) → CLOSED(false)。
    @Test
    void isRecruitmentOpen_締切前はtrueを返す() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 30, 23, 59);
        Tournament tournament = Tournament.create(
                "Test Cup", 3, LocalDateTime.of(2026, 10, 1, 19, 0), deadline, 8, "OPEN");

        assertTrue(tournament.isRecruitmentOpen(deadline.minusSeconds(1)));
    }

    @Test
    void isRecruitmentOpen_締切ちょうどはfalseを返す() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 30, 23, 59);
        Tournament tournament = Tournament.create(
                "Test Cup", 3, LocalDateTime.of(2026, 10, 1, 19, 0), deadline, 8, "OPEN");

        assertFalse(tournament.isRecruitmentOpen(deadline));
    }

    @Test
    void isRecruitmentOpen_締切後はfalseを返す() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 30, 23, 59);
        Tournament tournament = Tournament.create(
                "Test Cup", 3, LocalDateTime.of(2026, 10, 1, 19, 0), deadline, 8, "OPEN");

        assertFalse(tournament.isRecruitmentOpen(deadline.plusSeconds(1)));
    }
}
