package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class TeamMemberTest {

    @Test
    void create_指定したTeamとUserでTeamMemberを生成できる() {
        Tournament tournament = Tournament.create("Test Cup", 3, LocalDateTime.now(), 24, "OPEN");
        User owner = User.create(
                "Owner User",
                "owner@example.com",
                "hashed-password",
                List.of(new User.CharacterAssignment(new Character(1L, "Ryu"), "MASTER", 1600)),
                null,
                null,
                null
        );
        Team team = Team.create(tournament, owner, "Team Ryu", null, null, null);

        TeamMember teamMember = TeamMember.create(team, owner);

        assertEquals(team, teamMember.getTeam());
        assertEquals(owner, teamMember.getUser());
    }
}
