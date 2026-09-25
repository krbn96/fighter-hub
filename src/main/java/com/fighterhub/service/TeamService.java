package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.dto.TeamMemberResponse;
import com.fighterhub.dto.TeamResponse;
import com.fighterhub.dto.TeamUpdateRequest;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.InvalidRequestException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.CharacterRepository;
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

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TournamentRepository tournamentRepository,
            UserRepository userRepository,
            CharacterRepository characterRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.tournamentRepository = tournamentRepository;
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
    }

    @Transactional
    public TeamCreateResponse createTeam(Long userId, TeamCreateRequest request) {
        User owner = userRepository.findByIdAndDeleteFlagFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Tournament tournament = tournamentRepository.findByIdAndDeleteFlagFalse(request.tournamentId())
                .orElseThrow(() -> new TournamentNotFoundException(request.tournamentId()));

        if (teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(userId, tournament.getId())) {
            throw new DuplicateTournamentMembershipException(userId, tournament.getId());
        }

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
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(userId)) {
            throw new NotTeamOwnerException(userId, teamId);
        }

        if (!request.name().isUndefined()) {
            team.updateName(request.name().get());
        }

        if (!request.rankRequirement().isUndefined()) {
            team.updateRankRequirement(request.rankRequirement().get());
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
