package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.RecruitmentApplicationRepository;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.TournamentRepository;
import com.fighterhub.repository.UserRepository;

// TeamService#createTeamにおける「Team作成 + TeamMember作成 + 同一TournamentのPENDING
// RecruitmentApplication→REJECTED遷移」が単一トランザクションであることを実PostgreSQLで
// 検証する統合テスト。TeamServiceTestはMockitoベースの単体テストのため、呼び出し順序や
// 引数は検証できても実トランザクションの原子性までは検証できない。
//
// 途中失敗時のrollbackテストについては検討した上で追加していない。createTeam内で
// Team/TeamMemberの永続化より後に残る処理はrejectOtherPendingApplicationsInTournamentのみで、
// その内部のreject()はPESSIMISTIC_WRITEで取得した対象行が必ずPENDINGであることを前提にしており、
// 単一スレッドの通常実行ではApplicationAlreadyProcessedException等の例外を自然に起こせない
// (本番コードへテストのためだけの人工的なfailure hookは追加しない方針のため)。
// そのため正常系の統合テストのみを追加する。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + TeamServicePersistenceTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class TeamServicePersistenceTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private TeamService teamService;

    @Autowired
    private RecruitmentApplicationService recruitmentApplicationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private RecruitmentApplicationRepository recruitmentApplicationRepository;

    @Autowired
    private CharacterRepository characterRepository;

    private final List<Long> createdApplicationIds = new ArrayList<>();
    private final List<Long> createdTeamIds = new ArrayList<>();
    private final List<Long> createdTournamentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdApplicationIds.forEach(recruitmentApplicationRepository::deleteById);
        // TeamMemberはTeam削除のON DELETE CASCADE等に依存せず、Team削除前にTeam単位で削除する。
        createdTeamIds.forEach(teamId ->
                teamMemberRepository.findActiveTeamMembersByTeamId(teamId)
                        .forEach(member -> teamMemberRepository.deleteById(member.getId())));
        createdTeamIds.forEach(teamRepository::deleteById);
        createdTournamentIds.forEach(tournamentRepository::deleteById);
        createdUserIds.forEach(userRepository::deleteById);
    }

    @Test
    void createTeam_成功時にTeamとTeamMember作成および既存PENDINGのREJECTEDが同一トランザクションで確定する() {
        Long characterId = anyExistingCharacterId();
        User existingTeamOwner = createUser(characterId);
        User newTeamOwner = createUser(characterId);
        Tournament tournament = createTournament(4);

        TeamCreateResponse existingTeam = teamService.createTeam(
                existingTeamOwner.getId(),
                new TeamCreateRequest(tournament.getId(), "Existing Team " + UUID.randomUUID(), null, null, null));
        createdTeamIds.add(existingTeam.id());

        RecruitmentApplicationResponse application = recruitmentApplicationService.createApplication(
                newTeamOwner.getId(), existingTeam.id(),
                new RecruitmentApplicationCreateRequest("よろしくお願いします"));
        createdApplicationIds.add(application.id());
        assertEquals(RecruitmentApplicationStatus.PENDING, application.status());

        // newTeamOwnerは既存Teamへの申請がPENDINGのまま、同一Tournamentで自身のTeamを作成する。
        TeamCreateResponse newTeam = teamService.createTeam(
                newTeamOwner.getId(),
                new TeamCreateRequest(tournament.getId(), "New Team " + UUID.randomUUID(), null, null, null));
        createdTeamIds.add(newTeam.id());

        assertNotNull(newTeam.id());

        // Team作成(別Service呼び出し=別トランザクション)がコミットされていること自体の確認。
        assertTrue(teamRepository.findActiveTeamById(newTeam.id()).isPresent(), "新規Teamが永続化されているはず");
        assertTrue(
                teamMemberRepository.existsByTeam_IdAndUser_Id(newTeam.id(), newTeamOwner.getId()),
                "新規TeamのTeamMemberとしてnewTeamOwnerが永続化されているはず");

        // 同一Transaction内でPENDING→REJECTEDへの遷移もコミットされていること。
        RecruitmentApplication refreshedApplication =
                recruitmentApplicationRepository.findById(application.id()).orElseThrow();
        assertEquals(
                RecruitmentApplicationStatus.REJECTED,
                refreshedApplication.getStatus(),
                "Team作成と同一トランザクションで元のPENDING申請がREJECTEDになっているはず");
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private User createUser(Long characterId) {
        String email = "team-persistence-test-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest(
                email,
                "password123",
                "Team Persistence Test User",
                List.of(new UserCharacterRequest(characterId, "MASTER", 1600)),
                null,
                null,
                null
        );

        UserCreateResponse response = userService.createUser(request);
        createdUserIds.add(response.id());

        return userRepository.findById(response.id()).orElseThrow();
    }

    private Tournament createTournament(int teamSize) {
        // createTeamが実Serviceを通る(募集締切チェックが働く)ため、recruitmentDeadlineは
        // テスト実行時点より確実に未来(かつstartAtより前)にしておく。
        Tournament tournament = tournamentRepository.save(Tournament.create(
                "Team Persistence Test Cup " + UUID.randomUUID(),
                teamSize,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(1),
                24,
                "OPEN"
        ));
        createdTournamentIds.add(tournament.getId());

        return tournament;
    }
}
