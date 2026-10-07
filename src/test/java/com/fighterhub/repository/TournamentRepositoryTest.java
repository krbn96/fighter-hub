package com.fighterhub.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fighterhub.entity.Tournament;

// TournamentRepositoryのfindAllByDeleteFlagFalseが、実PostgreSQL上でも
// delete_flag=falseのTournamentのみを返すことを確認する統合テスト。
// 既存のTeamRepositoryTest等と同じ、ローカルPostgreSQLへ直接接続する
// @SpringBootTest方式・手動cleanup方式をそのまま踏襲している。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + TournamentRepositoryTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class TournamentRepositoryTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdTournamentIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdTournamentIds.forEach(tournamentRepository::deleteById);
    }

    @Test
    void findAllByDeleteFlagFalse_有効なTournamentのみ返しdeleteFlagtrueは除外する() {
        Tournament activeTournament = createTournament();
        Tournament deletedTournament = createTournament();

        jdbcTemplate.update("UPDATE t_tournaments SET delete_flag = true WHERE id = ?", deletedTournament.getId());

        List<Long> foundIds = tournamentRepository.findAllByDeleteFlagFalse().stream()
                .map(Tournament::getId)
                .toList();

        assertTrue(foundIds.contains(activeTournament.getId()));
        assertFalse(foundIds.contains(deletedTournament.getId()));
    }

    @Test
    void findAllByDeleteFlagFalseAndNameContainingIgnoreCase_大文字小文字を区別せず部分一致し論理削除済みは除外する() {
        String uniqueSuffix = UUID.randomUUID().toString();
        Tournament activeTournament = createTournament("STREET FIGHTER 6 CUP " + uniqueSuffix);
        Tournament deletedTournament = createTournament("STREET FIGHTER 6 CUP " + uniqueSuffix);
        Tournament unrelatedTournament = createTournament("TEKKEN 8 CUP " + uniqueSuffix);

        jdbcTemplate.update("UPDATE t_tournaments SET delete_flag = true WHERE id = ?", deletedTournament.getId());

        List<Long> foundIds = tournamentRepository
                .findAllByDeleteFlagFalseAndNameContainingIgnoreCase("street fighter")
                .stream()
                .map(Tournament::getId)
                .toList();

        assertTrue(foundIds.contains(activeTournament.getId()));
        assertFalse(foundIds.contains(deletedTournament.getId()));
        assertFalse(foundIds.contains(unrelatedTournament.getId()));
    }

    private Tournament createTournament() {
        return createTournament("Test Cup " + UUID.randomUUID());
    }

    private Tournament createTournament(String name) {
        Tournament tournament = tournamentRepository.save(Tournament.create(
                name, 3, LocalDateTime.now(), LocalDateTime.now().minusDays(1),
                24));
        createdTournamentIds.add(tournament.getId());

        return tournament;
    }
}
