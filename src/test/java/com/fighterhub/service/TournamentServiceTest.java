package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;

import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.dto.TournamentUpdateRequest;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.entity.UserRole;
import com.fighterhub.exception.InvalidRequestException;
import com.fighterhub.exception.NotAdminException;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.TournamentRepository;
import com.fighterhub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private TournamentService tournamentService;

    // name/recruitingを指定しないテストではClockの具体的な値を気にしないため、
    // TeamServiceTestと同じ方針でlenientな最低限のstubを用意する。
    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private User mockUser(UserRole role) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
        lenient().when(user.getRole()).thenReturn(role);
        return user;
    }

    private static final LocalDateTime SAMPLE_START_AT = LocalDateTime.of(2026, 10, 1, 19, 0);
    private static final LocalDateTime SAMPLE_RECRUITMENT_DEADLINE = LocalDateTime.of(2026, 9, 30, 23, 59);

    private static TournamentCreateRequest sampleCreateRequest() {
        return new TournamentCreateRequest(
                "STREET FIGHTER 6 CUP",
                3,
                SAMPLE_START_AT,
                SAMPLE_RECRUITMENT_DEADLINE,
                64
        );
    }

    private static TournamentUpdateRequest allUndefinedUpdateRequest() {
        return new TournamentUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    private Tournament newRealTournament() {
        return Tournament.create(
                "STREET FIGHTER 6 CUP", 3, SAMPLE_START_AT, SAMPLE_RECRUITMENT_DEADLINE, 64);
    }

    private Tournament mockTournament() {
        Tournament tournament = mock(Tournament.class);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(tournament.getId()).thenReturn(1L);
        when(tournament.getName()).thenReturn("STREET FIGHTER 6 CUP");
        when(tournament.getTeamSize()).thenReturn(3);
        when(tournament.getStartAt()).thenReturn(SAMPLE_START_AT);
        when(tournament.getRecruitmentDeadline()).thenReturn(SAMPLE_RECRUITMENT_DEADLINE);
        when(tournament.getMaxPlayers()).thenReturn(64);
        when(tournament.getCreatedAt()).thenReturn(now);
        when(tournament.getUpdatedAt()).thenReturn(now);
        return tournament;
    }

    @Test
    void findAllTournaments_nameとrecruiting未指定の場合_従来どおり全件を返す() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of(tournament));

        List<TournamentResponse> responses = tournamentService.findAllTournaments(null, null);

        assertEquals(1, responses.size());
        TournamentResponse response = responses.get(0);
        assertEquals(1L, response.id());
        assertEquals("STREET FIGHTER 6 CUP", response.name());
        assertEquals(3, response.teamSize());
        assertEquals(64, response.maxPlayers());
        verify(tournamentRepository, never()).findAllByDeleteFlagFalseAndNameContainingIgnoreCase(any());
    }

    @Test
    void findAllTournaments_0件の場合は空Listを返す() {
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of());

        List<TournamentResponse> responses = tournamentService.findAllTournaments(null, null);

        assertEquals(0, responses.size());
    }

    @Test
    void findAllTournaments_nameが空白のみの場合は条件なしとして全件取得する() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of(tournament));

        List<TournamentResponse> responses = tournamentService.findAllTournaments("   ", null);

        assertEquals(1, responses.size());
        verify(tournamentRepository, never()).findAllByDeleteFlagFalseAndNameContainingIgnoreCase(any());
    }

    @Test
    void findAllTournaments_name指定時はContainingIgnoreCase検索を前後空白を除いて使用する() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findAllByDeleteFlagFalseAndNameContainingIgnoreCase("street"))
                .thenReturn(List.of(tournament));

        List<TournamentResponse> responses = tournamentService.findAllTournaments("  street  ", null);

        assertEquals(1, responses.size());
        verify(tournamentRepository, times(1))
                .findAllByDeleteFlagFalseAndNameContainingIgnoreCase("street");
        verify(tournamentRepository, never()).findAllByDeleteFlagFalse();
    }

    @Test
    void findAllTournaments_recruitingがtrueの場合_募集中のみ返す() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Clock fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        TournamentService serviceWithFixedClock =
                new TournamentService(tournamentRepository, userRepository, fixedClock);

        Tournament recruiting = Tournament.create(
                "RECRUITING CUP", 3, now.plusDays(2), now.plusDays(1), 64);
        Tournament closed = Tournament.create(
                "CLOSED CUP", 3, now.plusDays(2), now.minusDays(1), 64);
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of(recruiting, closed));

        List<TournamentResponse> responses = serviceWithFixedClock.findAllTournaments(null, true);

        assertEquals(1, responses.size());
        assertEquals("RECRUITING CUP", responses.get(0).name());
    }

    @Test
    void findAllTournaments_recruitingがfalseの場合_募集終了のみ返す() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Clock fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        TournamentService serviceWithFixedClock =
                new TournamentService(tournamentRepository, userRepository, fixedClock);

        Tournament recruiting = Tournament.create(
                "RECRUITING CUP", 3, now.plusDays(2), now.plusDays(1), 64);
        Tournament closed = Tournament.create(
                "CLOSED CUP", 3, now.plusDays(2), now.minusDays(1), 64);
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of(recruiting, closed));

        List<TournamentResponse> responses = serviceWithFixedClock.findAllTournaments(null, false);

        assertEquals(1, responses.size());
        assertEquals("CLOSED CUP", responses.get(0).name());
    }

    // 既存のisRecruitmentOpen境界値仕様(締切ちょうどはCLOSED)と一致させる。
    @Test
    void findAllTournaments_recruitmentDeadlineが現在時刻ちょうどの場合_募集終了として扱う() {
        LocalDateTime deadline = LocalDateTime.of(2026, 1, 1, 0, 0);
        Clock fixedClock = Clock.fixed(deadline.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        TournamentService serviceWithFixedClock =
                new TournamentService(tournamentRepository, userRepository, fixedClock);

        Tournament tournament = Tournament.create(
                "DEADLINE CUP", 3, deadline.plusDays(1), deadline, 64);
        when(tournamentRepository.findAllByDeleteFlagFalse()).thenReturn(List.of(tournament));

        assertEquals(0, serviceWithFixedClock.findAllTournaments(null, true).size());
        assertEquals(1, serviceWithFixedClock.findAllTournaments(null, false).size());
    }

    @Test
    void findAllTournaments_nameとrecruitingを組み合わせた場合_両方の条件で絞り込む() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Clock fixedClock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        TournamentService serviceWithFixedClock =
                new TournamentService(tournamentRepository, userRepository, fixedClock);

        Tournament matching = Tournament.create(
                "STREET FIGHTER 6 CUP", 3, now.plusDays(2), now.plusDays(1), 64);
        Tournament nameMatchesButClosed = Tournament.create(
                "STREET FIGHTER 6 CUP OLD", 3, now.plusDays(2), now.minusDays(1), 64);
        when(tournamentRepository.findAllByDeleteFlagFalseAndNameContainingIgnoreCase("street"))
                .thenReturn(List.of(matching, nameMatchesButClosed));

        List<TournamentResponse> responses = serviceWithFixedClock.findAllTournaments("street", true);

        assertEquals(1, responses.size());
        assertEquals("STREET FIGHTER 6 CUP", responses.get(0).name());
    }

    @Test
    void findTournamentById_正常に取得できる() {
        Tournament tournament = mockTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = tournamentService.findTournamentById(1L);

        assertEquals(1L, response.id());
        assertEquals("STREET FIGHTER 6 CUP", response.name());
    }

    @Test
    void findTournamentById_存在しない場合_TournamentNotFoundExceptionを投げる() {
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(TournamentNotFoundException.class, () -> tournamentService.findTournamentById(999L));
    }

    @Test
    void createTournament_ADMINならTournamentを作成できる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament savedTournament = mockTournament();
        when(tournamentRepository.save(any(Tournament.class))).thenReturn(savedTournament);

        TournamentResponse response = tournamentService.createTournament(1L, sampleCreateRequest());

        assertEquals(1L, response.id());
        assertEquals("STREET FIGHTER 6 CUP", response.name());
        assertEquals(3, response.teamSize());
        assertEquals(SAMPLE_START_AT, response.startAt());
        assertEquals(SAMPLE_RECRUITMENT_DEADLINE, response.recruitmentDeadline());
        assertEquals(64, response.maxPlayers());

        ArgumentCaptor<Tournament> tournamentCaptor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository, times(1)).save(tournamentCaptor.capture());
        assertEquals("STREET FIGHTER 6 CUP", tournamentCaptor.getValue().getName());
        assertEquals(3, tournamentCaptor.getValue().getTeamSize());
        assertEquals(SAMPLE_RECRUITMENT_DEADLINE, tournamentCaptor.getValue().getRecruitmentDeadline());
        assertEquals(64, tournamentCaptor.getValue().getMaxPlayers());
    }

    @Test
    void createTournament_USERの場合_NotAdminExceptionを投げsaveは呼ばれない() {
        User user = mockUser(UserRole.USER);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                NotAdminException.class,
                () -> tournamentService.createTournament(1L, sampleCreateRequest()));

        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void createTournament_Userが存在しない場合_UserNotFoundExceptionを投げsaveは呼ばれない() {
        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> tournamentService.createTournament(999L, sampleCreateRequest()));

        verify(tournamentRepository, never()).save(any());
    }

    // 正式business rule: recruitmentDeadline < startAt(同時刻不可)。
    @Test
    void createTournament_recruitmentDeadlineがstartAtと同時刻の場合_InvalidRequestExceptionを投げsaveは呼ばれない() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        TournamentCreateRequest request = new TournamentCreateRequest(
                "STREET FIGHTER 6 CUP", 3, SAMPLE_START_AT, SAMPLE_START_AT, 64);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> tournamentService.createTournament(1L, request));

        assertEquals("recruitmentDeadline must be before startAt", exception.getMessage());
        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void createTournament_recruitmentDeadlineがstartAtより後の場合_InvalidRequestExceptionを投げsaveは呼ばれない() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        TournamentCreateRequest request = new TournamentCreateRequest(
                "STREET FIGHTER 6 CUP", 3, SAMPLE_START_AT, SAMPLE_START_AT.plusMinutes(1), 64);

        assertThrows(
                InvalidRequestException.class,
                () -> tournamentService.createTournament(1L, request));

        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void updateTournament_ADMINなら指定フィールドを更新でき未指定フィールドは変更されない() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        TournamentUpdateRequest request = new TournamentUpdateRequest(
                JsonNullable.of("New Cup Name"),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        TournamentResponse response = tournamentService.updateTournament(1L, 100L, request);

        assertEquals("New Cup Name", response.name());
        // 未指定フィールドは変更されない。
        assertEquals(3, response.teamSize());
        assertEquals(SAMPLE_START_AT, response.startAt());
        assertEquals(SAMPLE_RECRUITMENT_DEADLINE, response.recruitmentDeadline());
        assertEquals(64, response.maxPlayers());
        verify(tournamentRepository, times(1)).flush();
    }

    @Test
    void updateTournament_USERの場合_NotAdminExceptionを投げTournament取得もflushも行われない() {
        User user = mockUser(UserRole.USER);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                NotAdminException.class,
                () -> tournamentService.updateTournament(1L, 100L, allUndefinedUpdateRequest()));

        verify(tournamentRepository, never()).findByIdAndDeleteFlagFalse(any());
        verify(tournamentRepository, never()).flush();
    }

    @Test
    void updateTournament_Tournamentが存在しない場合_TournamentNotFoundExceptionを投げる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> tournamentService.updateTournament(1L, 999L, allUndefinedUpdateRequest()));

        verify(tournamentRepository, never()).flush();
    }

    // 正式business rule: PATCHでは「更新適用後のTournamentの実効値」で
    // recruitmentDeadline < startAtを検証する(requestの生の値同士だけを比較してはいけない)。
    @Test
    void updateTournament_startAtのみ変更して既存recruitmentDeadline基準で不正になる場合_InvalidRequestExceptionを投げる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        // 既存: startAt=2026-10-01T19:00, recruitmentDeadline=2026-09-30T23:59。
        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        // startAtだけを既存recruitmentDeadlineより前(2026-09-30T00:00)へ変更すると不正になる。
        TournamentUpdateRequest request = new TournamentUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(LocalDateTime.of(2026, 9, 30, 0, 0)),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> tournamentService.updateTournament(1L, 100L, request));

        assertEquals("recruitmentDeadline must be before startAt", exception.getMessage());
        verify(tournamentRepository, never()).flush();
    }

    @Test
    void updateTournament_recruitmentDeadlineのみ変更して既存startAt基準で不正になる場合_InvalidRequestExceptionを投げる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        // 既存: startAt=2026-10-01T19:00。
        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        // recruitmentDeadlineだけを既存startAtより後(2026-10-02T00:00)へ変更すると不正になる。
        TournamentUpdateRequest request = new TournamentUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(LocalDateTime.of(2026, 10, 2, 0, 0)),
                JsonNullable.undefined()
        );

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> tournamentService.updateTournament(1L, 100L, request));

        assertEquals("recruitmentDeadline must be before startAt", exception.getMessage());
        verify(tournamentRepository, never()).flush();
    }

    @Test
    void updateTournament_startAtとrecruitmentDeadlineの両方を変更して正常になる場合_更新できる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        LocalDateTime newStartAt = LocalDateTime.of(2026, 11, 1, 19, 0);
        LocalDateTime newDeadline = LocalDateTime.of(2026, 10, 31, 23, 59);
        TournamentUpdateRequest request = new TournamentUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(newStartAt),
                JsonNullable.of(newDeadline),
                JsonNullable.undefined()
        );

        TournamentResponse response = tournamentService.updateTournament(1L, 100L, request);

        assertEquals(newStartAt, response.startAt());
        assertEquals(newDeadline, response.recruitmentDeadline());
        verify(tournamentRepository, times(1)).flush();
    }

    @Test
    void deleteTournament_ADMINならdeleteFlagがtrueになりRepositoryのdeleteは使用されない() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));

        Tournament tournament = newRealTournament();
        when(tournamentRepository.findByIdAndDeleteFlagFalse(100L)).thenReturn(Optional.of(tournament));

        tournamentService.deleteTournament(1L, 100L);

        assertTrue(tournament.isDeleteFlag());
        verify(tournamentRepository, never()).delete(any());
        verify(tournamentRepository, never()).deleteById(any());
    }

    @Test
    void deleteTournament_USERの場合_NotAdminExceptionを投げTournamentは取得されない() {
        User user = mockUser(UserRole.USER);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                NotAdminException.class,
                () -> tournamentService.deleteTournament(1L, 100L));

        verify(tournamentRepository, never()).findByIdAndDeleteFlagFalse(any());
    }

    @Test
    void deleteTournament_Tournamentが存在しないまたは既削除の場合_TournamentNotFoundExceptionを投げる() {
        User admin = mockUser(UserRole.ADMIN);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(
                TournamentNotFoundException.class,
                () -> tournamentService.deleteTournament(1L, 999L));
    }
}
