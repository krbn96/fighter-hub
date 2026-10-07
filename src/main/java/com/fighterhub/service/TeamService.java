package com.fighterhub.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.dto.TeamMemberResponse;
import com.fighterhub.dto.TeamResponse;
import com.fighterhub.dto.TeamUpdateRequest;
import com.fighterhub.entity.Rank;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.exception.CannotRemoveTeamOwnerException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.InvalidRequestException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentClosedException;
import com.fighterhub.exception.TeamMemberNotFoundException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.TeamActiveMemberCount;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.TournamentRepository;
import com.fighterhub.repository.UserRepository;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final RecruitmentApplicationService recruitmentApplicationService;
    private final Clock clock;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentRepository tournamentRepository,
            UserRepository userRepository,
            CharacterRepository characterRepository,
            RecruitmentApplicationService recruitmentApplicationService,
            Clock clock) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentRepository = tournamentRepository;
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
        this.recruitmentApplicationService = recruitmentApplicationService;
        this.clock = clock;
    }

    @Transactional
    public TeamCreateResponse createTeam(Long userId, TeamCreateRequest request) {
        User owner = userRepository.findByIdAndDeleteFlagFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Tournament tournament = tournamentRepository.findByIdAndDeleteFlagFalse(request.tournamentId())
                .orElseThrow(() -> new TournamentNotFoundException(request.tournamentId()));

        // 正式business rule: 募集締切(recruitmentDeadline)後の新規Team作成は禁止する
        // (締切ちょうども不可。Tournament.isRecruitmentOpenがnow.isBefore(deadline)で判定する)。
        if (!tournament.isRecruitmentOpen(LocalDateTime.now(clock))) {
            throw new RecruitmentClosedException(tournament.getId());
        }

        if (teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(userId, tournament.getId())) {
            throw new DuplicateTournamentMembershipException(userId, tournament.getId());
        }

        validateRankRequirement(request.rankRequirement());
        validateCharacterRequirements(request.characterRequirements());

        Team team = Team.create(
                tournament,
                owner,
                request.name(),
                request.rankRequirement(),
                request.characterRequirements(),
                request.recruitmentMessage()
        );
        Team savedTeam = teamRepository.save(team);

        teamMemberRepository.save(TeamMember.create(savedTeam, owner));

        // 正式business rule: 同一TournamentでUserがTeamMemberになった時点で、
        // そのUserが同一Tournament内の他TeamへのPENDING申請はREJECTEDへ遷移する。
        // Team作成によってownerは即座にTeamMemberになるため、ここでも同じ処理を行う。
        recruitmentApplicationService.rejectOtherPendingApplicationsInTournament(
                owner.getId(), tournament.getId());

        return toTeamCreateResponse(savedTeam);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> findAllTeams() {
        return teamRepository.findAllActiveTeams().stream()
                .map(this::toTeamResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse findTeamById(Long teamId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        return toTeamResponse(team);
    }

    // name/characterId/rank/availableはすべて任意(未指定時は絞り込まない)。複数条件指定時はAND。
    @Transactional(readOnly = true)
    public List<TeamResponse> findTeamsByTournament(
            Long tournamentId, String name, Long characterId, String rank, Boolean available) {
        tournamentRepository.findByIdAndDeleteFlagFalse(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException(tournamentId));

        String trimmedName = name == null ? null : name.trim();

        List<Team> teams = (trimmedName == null || trimmedName.isEmpty())
                ? teamRepository.findActiveTeamsByTournamentId(tournamentId)
                : teamRepository.findActiveTeamsByTournamentIdAndNameContainingIgnoreCase(
                        tournamentId, trimmedName);

        // characterIdはTeam.characterRequirements(JSONB配列)への単純な包含チェック。
        // 存在しないcharacterId等を指定した場合もエラーにはせず、該当チームが0件の検索結果として
        // 扱う(nameのような他の検索条件と同じ方針。characterRequirements自体は書き込み時に
        // CharacterRepositoryで実在確認済みのため、検索時に再度存在確認する必要はない)。
        if (characterId != null) {
            teams = teams.stream()
                    .filter(team -> team.getCharacterRequirements() != null
                            && team.getCharacterRequirements().contains(characterId))
                    .toList();
        }

        if (rank != null) {
            Rank playerRank = Rank.fromValue(rank)
                    .orElseThrow(() -> new InvalidRequestException("Invalid rank specified: " + rank));

            teams = teams.stream()
                    .filter(team -> satisfiesRankRequirement(playerRank, team.getRankRequirement()))
                    .toList();
        }

        if (Boolean.TRUE.equals(available)) {
            teams = filterByAvailability(teams);
        }

        return teams.stream()
                .map(this::toTeamResponse)
                .toList();
    }

    // 「そのランクのプレイヤーが応募条件を満たせるチーム」= rankRequirementが未指定(指定なし)の
    // チーム、またはplayerRankがrankRequirement以上のチーム。
    // rankRequirementはvalidateRankRequirementにより書き込み時点でRank enumの値のみに限定されるが、
    // この検証を追加する前に保存された既存データがRankへ変換できない値を保持している可能性に備え、
    // そのようなデータは満たせるかどうかを判定できないため対象外として扱う(防御的な分岐)。
    private boolean satisfiesRankRequirement(Rank playerRank, String rankRequirement) {
        if (rankRequirement == null) {
            return true;
        }

        return Rank.fromValue(rankRequirement)
                .map(playerRank::satisfies)
                .orElse(false);
    }

    // 「現在有効なメンバー数」(TeamMemberに紐づくUserのdelete_flag=false)がTournament.teamSize
    // 未満のTeamのみに絞り込む。対象Team群の有効メンバー数は、Teamごとのcount queryをループ内から
    // 呼ぶ(N+1)のではなく、countActiveMembersByTeamIdsで1回のクエリにまとめて取得する。
    // 応募承認時の定員判定(RecruitmentApplicationService)は単一Teamの判定のため、
    // 既存のcountByTeam_IdAndUser_DeleteFlagFalseをそのまま使用する(こちらは変更しない)。
    private List<Team> filterByAvailability(List<Team> teams) {
        if (teams.isEmpty()) {
            return teams;
        }

        List<Long> teamIds = teams.stream().map(Team::getId).toList();

        // GROUP BYのため、有効なTeamMemberが1人も存在しないTeamは結果に含まれない。
        // そのTeamはgetOrDefaultで0人として扱う(TeamMember 0人のTeamも正しく空きあり扱いになる)。
        Map<Long, Long> activeMemberCountByTeamId = teamMemberRepository.countActiveMembersByTeamIds(teamIds)
                .stream()
                .collect(Collectors.toMap(TeamActiveMemberCount::getTeamId, TeamActiveMemberCount::getActiveMemberCount));

        return teams.stream()
                .filter(team -> activeMemberCountByTeamId.getOrDefault(team.getId(), 0L)
                        < team.getTournament().getTeamSize())
                .toList();
    }

    // rankRequirementはnull(指定なし)を許可し、null以外はRank enum(8値)のいずれかであることを
    // 検証する(ランク定義をここで重複させず、既存のRank enumを再利用する)。
    private void validateRankRequirement(String rankRequirement) {
        if (rankRequirement != null && !Rank.isValid(rankRequirement)) {
            throw new InvalidRequestException("Invalid rankRequirement specified: " + rankRequirement);
        }
    }

    // 「ownerであるTeam」ではなく、T_TEAM_MEMBERSを基準に所属しているTeamを返す。
    // ownerも自身のTeamのTeamMemberとして登録されているため、この検索だけでowner分も含まれる。
    @Transactional(readOnly = true)
    public List<TeamResponse> findMyTeams(Long userId) {
        userRepository.findByIdAndDeleteFlagFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return teamMemberRepository.findActiveTeamMembershipsByUserId(userId).stream()
                .map(TeamMember::getTeam)
                .map(this::toTeamResponse)
                .toList();
    }

    @Transactional
    public TeamResponse updateTeam(Long userId, Long teamId, TeamUpdateRequest request) {
        Team team = getTeamOwnedBy(userId, teamId);

        if (!request.name().isUndefined()) {
            team.updateName(request.name().get());
        }

        if (!request.rankRequirement().isUndefined()) {
            String rankRequirement = request.rankRequirement().get();
            validateRankRequirement(rankRequirement);
            team.updateRankRequirement(rankRequirement);
        }

        if (!request.characterRequirements().isUndefined()) {
            List<Long> characterIds = request.characterRequirements().get();
            validateCharacterRequirements(characterIds);
            team.updateCharacterRequirements(characterIds);
        }

        if (!request.recruitmentMessage().isUndefined()) {
            team.updateRecruitmentMessage(request.recruitmentMessage().get());
        }

        // dirty checkingによるUPDATEはtransactionコミット時までflushされないため、
        // flushしないまま生成すると@UpdateTimestampが未反映のupdatedAtをレスポンスに含めてしまう。
        teamRepository.flush();

        return toTeamResponse(team);
    }

    // Teamの有効性確認(存在・delete_flag・Tournament/ownerの有効性)は
    // findActiveTeamByIdへ委譲する。メンバー0人は正常系として空Listを返す。
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> findTeamMembers(Long teamId) {
        teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        return teamMemberRepository.findActiveTeamMembersByTeamId(teamId).stream()
                .map(this::toTeamMemberResponse)
                .toList();
    }

    @Transactional
    public void removeTeamMember(Long userId, Long teamId, Long targetUserId) {
        Team team = getTeamOwnedBy(userId, teamId);

        if (targetUserId.equals(team.getOwner().getId())) {
            throw new CannotRemoveTeamOwnerException(teamId, targetUserId);
        }

        TeamMember teamMember = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, targetUserId)
                .orElseThrow(() -> new TeamMemberNotFoundException(teamId, targetUserId));

        teamMemberRepository.delete(teamMember);
    }

    // Teamの存在確認とowner判定を共通化するhelper。
    // updateTeam / removeTeamMemberの両方から利用する。
    private Team getTeamOwnedBy(Long userId, Long teamId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(userId)) {
            throw new NotTeamOwnerException(userId, teamId);
        }

        return team;
    }

    // characterRequirementsはDB上JSONBで保持しFK制約を持たないため、
    // 指定された各Character IDがM_CHARACTERSに実在するかをここで検証する。
    // null/空リストは「キャラクター条件なし」として検証をスキップする。
    private void validateCharacterRequirements(List<Long> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) {
            return;
        }

        for (Long characterId : characterIds) {
            if (!characterRepository.existsById(characterId)) {
                throw new InvalidRequestException("Character not found. character_id=" + characterId);
            }
        }
    }

    private TeamCreateResponse toTeamCreateResponse(Team team) {
        return new TeamCreateResponse(
                team.getId(),
                team.getTournament().getId(),
                team.getOwner().getId(),
                team.getName(),
                team.getRankRequirement(),
                team.getCharacterRequirements(),
                team.getRecruitmentMessage(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    private TeamMemberResponse toTeamMemberResponse(TeamMember teamMember) {
        return new TeamMemberResponse(
                teamMember.getUser().getId(),
                teamMember.getUser().getName(),
                teamMember.getJoinedAt()
        );
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getTournament().getId(),
                team.getTournament().getName(),
                team.getOwner().getId(),
                team.getOwner().getName(),
                team.getName(),
                team.getRankRequirement(),
                team.getCharacterRequirements(),
                team.getRecruitmentMessage(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
