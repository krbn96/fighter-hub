package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fighterhub.exception.ApplicationAlreadyProcessedException;

class RecruitmentApplicationTest {

    private static Team newTeam() {
        Tournament tournament = Tournament.create(
                "Test Cup", 3, LocalDateTime.now(), LocalDateTime.now().minusDays(1), 24, "OPEN");
        User owner = User.create(
                "Owner User",
                "owner@example.com",
                "hashed-password",
                List.of(new User.CharacterAssignment(new Character(1L, "Ryu"), "MASTER", 1600)),
                null,
                null,
                null
        );
        return Team.create(tournament, owner, "Team Ryu", null, null, null);
    }

    private static User newApplicant() {
        return User.create(
                "Applicant User",
                "applicant@example.com",
                "hashed-password",
                List.of(new User.CharacterAssignment(new Character(2L, "Ken"), "MASTER", 1600)),
                null,
                null,
                null
        );
    }

    @Test
    void create_statusはPENDINGで生成される() {
        RecruitmentApplication application = RecruitmentApplication.create(newTeam(), newApplicant(), "よろしくお願いします");

        assertEquals(RecruitmentApplicationStatus.PENDING, application.getStatus());
    }

    @Test
    void create_teamとuserとmessageが正しく保持される() {
        Team team = newTeam();
        User applicant = newApplicant();

        RecruitmentApplication application = RecruitmentApplication.create(team, applicant, "よろしくお願いします");

        assertEquals(team, application.getTeam());
        assertEquals(applicant, application.getUser());
        assertEquals("よろしくお願いします", application.getMessage());
    }

    @Test
    void create_messageがnullでも生成できる() {
        RecruitmentApplication application = RecruitmentApplication.create(newTeam(), newApplicant(), null);

        assertNull(application.getMessage());
    }

    @Test
    void approve_PENDINGからAPPROVEDへ遷移できる() {
        RecruitmentApplication application = RecruitmentApplication.create(newTeam(), newApplicant(), null);

        application.approve();

        assertEquals(RecruitmentApplicationStatus.APPROVED, application.getStatus());
    }

    @Test
    void reject_PENDINGからREJECTEDへ遷移できる() {
        RecruitmentApplication application = RecruitmentApplication.create(newTeam(), newApplicant(), null);

        application.reject();

        assertEquals(RecruitmentApplicationStatus.REJECTED, application.getStatus());
    }

    @Test
    void APPROVED状態からのapprove_rejectはいずれもApplicationAlreadyProcessedExceptionを投げる() {
        RecruitmentApplication application = RecruitmentApplication.create(newTeam(), newApplicant(), null);
        application.approve();

        assertThrows(ApplicationAlreadyProcessedException.class, application::approve);
        assertThrows(ApplicationAlreadyProcessedException.class, application::reject);
        assertEquals(RecruitmentApplicationStatus.APPROVED, application.getStatus());
    }

    @Test
    void REJECTED状態からのapprove_rejectはいずれもApplicationAlreadyProcessedExceptionを投げる() {
        RecruitmentApplication application = RecruitmentApplication.create(newTeam(), newApplicant(), null);
        application.reject();

        assertThrows(ApplicationAlreadyProcessedException.class, application::approve);
        assertThrows(ApplicationAlreadyProcessedException.class, application::reject);
        assertEquals(RecruitmentApplicationStatus.REJECTED, application.getStatus());
    }
}
