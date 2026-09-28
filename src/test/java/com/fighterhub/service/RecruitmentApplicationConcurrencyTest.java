package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.TeamFullException;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.RecruitmentApplicationRepository;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.TournamentRepository;
import com.fighterhub.repository.UserRepository;

// Day 6で導入したPessimistic Lockが、実PostgreSQL + 実Transaction(各Threadが
// RecruitmentApplicationServiceのプロキシ経由で独立した@Transactionalを開始する)の下で
// 実際に整合性を守ることを確認する統合テスト。CountDownLatchで開始タイミングをそろえ、
// Thread.sleepへの依存や無限待機を避けるため、Future#get()には必ずタイムアウトを設ける。
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=" + RecruitmentApplicationConcurrencyTest.TEST_ONLY_JWT_SECRET,
        "jwt.expiration=24h"
})
class RecruitmentApplicationConcurrencyTest {

    static final String TEST_ONLY_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Autowired
    private RecruitmentApplicationService recruitmentApplicationService;

    @Autowired
    private RecruitmentApplicationRepository recruitmentApplicationRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    private final List<Long> createdApplicationIds = new ArrayList<>();
    // approve成功時にServiceが内部でTeamMemberを作成するため、既知IDとの重複追加によるcleanUp時の
    // 二重delete(EmptyResultDataAccessException)を避けるためSetにする。
    private final Set<Long> createdTeamMemberIds = new LinkedHashSet<>();
    private final List<Long> createdTeamIds = new ArrayList<>();
    private final List<Long> createdTournamentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdApplicationIds.forEach(recruitmentApplicationRepository::deleteById);
        createdTeamMemberIds.forEach(teamMemberRepository::deleteById);
        createdTeamIds.forEach(teamRepository::deleteById);
        createdTournamentIds.forEach(tournamentRepository::deleteById);
        createdUserIds.forEach(userRepository::deleteById);
    }

    // シナリオA: 同一Userが同一Tournamentの異なるTeamへほぼ同時にcreateApplicationする。
    // 期待: 片方だけ成功しPENDINGとして保存され、もう片方はDuplicatePendingApplicationExceptionになる。
    // 最終的にそのUser+Tournamentで残るPENDING Applicationは1件だけであること。
    @Test
    void createApplication_同一Userが同一Tournamentの異なるTeamへ同時申請した場合_片方だけ成功しPENDINGは1件だけ残る() throws Exception {
        Long characterId = anyExistingCharacterId();
        User owner1 = createUser(characterId);
        User owner2 = createUser(characterId);
        User applicant = createUser(characterId);
        Tournament tournament = createTournament(3);
        Team team1 = createTeam(tournament, owner1);
        Team team2 = createTeam(tournament, owner2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> taskToTeam1 = createApplicationTask(
                    applicant.getId(), team1.getId(), readyLatch, startSignal);
            Callable<Object> taskToTeam2 = createApplicationTask(
                    applicant.getId(), team2.getId(), readyLatch, startSignal);

            Future<Object> future1 = executor.submit(taskToTeam1);
            Future<Object> future2 = executor.submit(taskToTeam2);

            // 両Threadが実行直前で待機状態に入るのを待ってから、同時に解放する。
            assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
            startSignal.countDown();

            Object result1 = future1.get(10, TimeUnit.SECONDS);
            Object result2 = future2.get(10, TimeUnit.SECONDS);

            for (Object result : List.of(result1, result2)) {
                if (result instanceof RecruitmentApplicationResponse response) {
                    createdApplicationIds.add(response.id());
                }
            }

            int successCount = countSuccesses(result1, result2);
            int conflictCount = countConflicts(result1, result2, DuplicatePendingApplicationException.class);

            assertEquals(1, successCount, "片方だけ成功しているはず");
            assertEquals(1, conflictCount, "もう片方はDuplicatePendingApplicationExceptionになるはず");
        } finally {
            executor.shutdownNow();
        }

        // OSIV無効のため、Repositoryから返ったEntityのLAZY関連(team.getTournament())へは
        // セッション外からアクセスできない。team.getId()(識別子のみ)はセッションなしでも
        // 取得できるため、team1/team2のIDでの絞り込みに留める。
        long pendingCount = recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(applicant.getId())
                .stream()
                .filter(a -> a.getStatus() == RecruitmentApplicationStatus.PENDING)
                .filter(a -> a.getTeam().getId().equals(team1.getId()) || a.getTeam().getId().equals(team2.getId()))
                .count();
        assertEquals(1, pendingCount, "最終的なPENDING Applicationは1件だけであるはず");
    }

    // シナリオB: Team定員まで残り1枠の状態で、異なる2つのApplicationをほぼ同時にapproveする。
    // 期待: 片方だけAPPROVEDになりもう片方はTeamFullExceptionになる。
    // TeamMember数はTournament.teamSizeを超えない。
    @Test
    void approveApplication_残り1枠のTeamへ異なる2Applicationを同時approveした場合_片方だけAPPROVEDになりTeamMember数は定員を超えない()
            throws Exception {
        Long characterId = anyExistingCharacterId();
        User owner = createUser(characterId);
        User applicant1 = createUser(characterId);
        User applicant2 = createUser(characterId);
        // teamSize=2、既存TeamMember(owner)1人 → 残り1枠の状態を作る。
        Tournament tournament = createTournament(2);
        Team team = createTeam(tournament, owner);
        createTeamMember(team, owner);

        RecruitmentApplication application1 = createPendingApplication(team, applicant1);
        RecruitmentApplication application2 = createPendingApplication(team, applicant2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> approveTask1 = approveApplicationTask(
                    owner.getId(), team.getId(), application1.getId(), readyLatch, startSignal);
            Callable<Object> approveTask2 = approveApplicationTask(
                    owner.getId(), team.getId(), application2.getId(), readyLatch, startSignal);

            Future<Object> future1 = executor.submit(approveTask1);
            Future<Object> future2 = executor.submit(approveTask2);

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
            startSignal.countDown();

            Object result1 = future1.get(10, TimeUnit.SECONDS);
            Object result2 = future2.get(10, TimeUnit.SECONDS);

            int successCount = countSuccesses(result1, result2);
            int conflictCount = countConflicts(result1, result2, TeamFullException.class);

            assertEquals(1, successCount, "片方だけAPPROVEDになっているはず");
            assertEquals(1, conflictCount, "もう片方はTeamFullExceptionになるはず");
        } finally {
            executor.shutdownNow();
        }

        long memberCount = teamMemberRepository.countByTeam_Id(team.getId());
        assertEquals(2, memberCount, "TeamMember数はTournament.teamSize(2)を超えないはず");

        // approveの成功で新規作成されたTeamMember(承認された側)はテストコードから直接
        // save()していないため、cleanUp()でのFK制約違反を避けるために改めて取得し登録する。
        teamMemberRepository.findActiveTeamMembersByTeamId(team.getId())
                .forEach(member -> createdTeamMemberIds.add(member.getId()));
    }

    private Callable<Object> createApplicationTask(
            Long userId, Long teamId, CountDownLatch readyLatch, CountDownLatch startSignal) {
        return () -> {
            readyLatch.countDown();
            startSignal.await(10, TimeUnit.SECONDS);
            try {
                return recruitmentApplicationService.createApplication(
                        userId, teamId, new RecruitmentApplicationCreateRequest("よろしくお願いします"));
            } catch (RuntimeException e) {
                return e;
            }
        };
    }

    private Callable<Object> approveApplicationTask(
            Long currentUserId, Long teamId, Long applicationId,
            CountDownLatch readyLatch, CountDownLatch startSignal) {
        return () -> {
            readyLatch.countDown();
            startSignal.await(10, TimeUnit.SECONDS);
            try {
                return recruitmentApplicationService.approveApplication(currentUserId, teamId, applicationId);
            } catch (RuntimeException e) {
                return e;
            }
        };
    }

    private int countSuccesses(Object... results) {
        int count = 0;
        for (Object result : results) {
            if (!(result instanceof Exception)) {
                count++;
            }
        }
        return count;
    }

    private int countConflicts(Object result1, Object result2, Class<? extends Exception> expectedType) {
        int count = 0;
        for (Object result : List.of(result1, result2)) {
            if (result instanceof Exception) {
                assertInstanceOf(expectedType, result, "想定外の例外が発生した: " + result);
                count++;
            }
        }
        return count;
    }

    private Long anyExistingCharacterId() {
        List<Character> characters = characterRepository.findAll();
        assumeFalse(characters.isEmpty(), "M_CHARACTERSにデータが存在しないため統合テストをスキップする");
        return characters.get(0).getId();
    }

    private User createUser(Long characterId) {
        String email = "recruitment-application-concurrency-test-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest request = new UserCreateRequest(
                email,
                "password123",
                "Concurrency Test User",
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
        Tournament tournament = tournamentRepository.save(Tournament.create(
                "Concurrency Test Cup " + UUID.randomUUID(),
                teamSize,
                LocalDateTime.now(),
                24,
                "OPEN"
        ));
        createdTournamentIds.add(tournament.getId());

        return tournament;
    }

    private Team createTeam(Tournament tournament, User owner) {
        Team team = teamRepository.save(Team.create(
                tournament, owner, "Concurrency Test Team " + UUID.randomUUID(), null, null, null));
        createdTeamIds.add(team.getId());

        return team;
    }

    private void createTeamMember(Team team, User user) {
        TeamMember teamMember = teamMemberRepository.save(TeamMember.create(team, user));
        createdTeamMemberIds.add(teamMember.getId());
    }

    private RecruitmentApplication createPendingApplication(Team team, User user) {
        RecruitmentApplication application =
                recruitmentApplicationRepository.save(RecruitmentApplication.create(team, user, null));
        createdApplicationIds.add(application.getId());

        return application;
    }
}
