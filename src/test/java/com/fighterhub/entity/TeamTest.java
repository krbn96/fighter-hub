package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class TeamTest {

    private static Tournament newTournament() {
        return Tournament.create("Test Cup", 3, LocalDateTime.now(), 24, "OPEN");
    }

    private static User newOwner() {
        return User.create(
                "Owner User",
                "owner@example.com",
                "hashed-password",
                List.of(new User.CharacterAssignment(new Character(1L, "Ryu"), "MASTER", 1600)),
                null,
                null,
                null
        );
    }

    @Test
    void create_指定した値でTeamを生成できる() {
        Tournament tournament = newTournament();
        User owner = newOwner();

        Team team = Team.create(
                tournament,
                owner,
                "Team Ryu",
                "MASTER",
                List.of(1L, 2L),
                "誰でも歓迎です"
        );

        assertEquals(tournament, team.getTournament());
        assertEquals(owner, team.getOwner());
        assertEquals("Team Ryu", team.getName());
        assertEquals("MASTER", team.getRankRequirement());
        assertEquals(List.of(1L, 2L), team.getCharacterRequirements());
        assertEquals("誰でも歓迎です", team.getRecruitmentMessage());
    }

    @Test
    void create_任意項目を指定しない場合_nullのまま保持される() {
        Tournament tournament = newTournament();
        User owner = newOwner();

        Team team = Team.create(tournament, owner, "Team Ken", null, null, null);

        assertNull(team.getRankRequirement());
        assertNull(team.getCharacterRequirements());
        assertNull(team.getRecruitmentMessage());
    }

    @Test
    void create_deleteFlagの初期値はfalseである() {
        Team team = Team.create(newTournament(), newOwner(), "Team Chun-Li", null, null, null);

        assertFalse(team.isDeleteFlag());
    }
}
