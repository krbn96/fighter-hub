package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.User;
import com.fighterhub.exception.ApplicationAlreadyProcessedException;
import com.fighterhub.exception.CannotApplyToOwnTeamException;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentApplicationNotFoundException;
import com.fighterhub.exception.TeamFullException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.RecruitmentApplicationRepository;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.UserRepository;

@Service
public class RecruitmentApplicationService {

    private final RecruitmentApplicationRepository recruitmentApplicationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public RecruitmentApplicationService(
            RecruitmentApplicationRepository recruitmentApplicationRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository) {
        this.recruitmentApplicationRepository = recruitmentApplicationRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RecruitmentApplicationResponse createApplication(
            Long userId, Long teamId, RecruitmentApplicationCreateRequest request) {

        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (team.getOwner().getId().equals(userId)) {
            throw new CannotApplyToOwnTeamException(userId, teamId);
        }

        if (teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(userId, team.getTournament().getId())) {
            throw new DuplicateTournamentMembershipException(userId, team.getTournament().getId());
        }

        if (recruitmentApplicationRepository.existsByTeam_IdAndUser_IdAndStatus(
                teamId, userId, RecruitmentApplicationStatus.PENDING)) {
            throw new DuplicatePendingApplicationException(teamId, userId);
        }

        User user = userRepository.findByIdAndDeleteFlagFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        RecruitmentApplication application = RecruitmentApplication.create(team, user, request.message());
        RecruitmentApplication savedApplication = recruitmentApplicationRepository.save(application);

        return toRecruitmentApplicationResponse(savedApplication);
    }

    @Transactional(readOnly = true)
    public List<RecruitmentApplicationResponse> findTeamApplications(Long userId, Long teamId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(userId)) {
            throw new NotTeamOwnerException(userId, teamId);
        }

        return recruitmentApplicationRepository.findByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(this::toRecruitmentApplicationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecruitmentApplicationResponse> findMyApplications(Long userId) {
        return recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toRecruitmentApplicationResponse)
                .toList();
    }

    // TeamMember作成とApplicationのAPPROVED変更は同一Transaction内で行い、
    // 片方だけ成功する状態を作らない。
    @Transactional
    public RecruitmentApplicationResponse approveApplication(Long currentUserId, Long teamId, Long applicationId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(currentUserId)) {
            throw new NotTeamOwnerException(currentUserId, teamId);
        }

        RecruitmentApplication application = recruitmentApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RecruitmentApplicationNotFoundException(teamId, applicationId));

        // 別TeamのApplicationであることを外部へ公開しないため、404として扱う。
        if (!application.getTeam().getId().equals(teamId)) {
            throw new RecruitmentApplicationNotFoundException(teamId, applicationId);
        }

        if (application.getStatus() != RecruitmentApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException(applicationId);
        }

        // 申請から承認までの間に別Teamへ加入している可能性があるため、承認時にも再確認する。
        if (teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(
                application.getUser().getId(), team.getTournament().getId())) {
            throw new DuplicateTournamentMembershipException(
                    application.getUser().getId(), team.getTournament().getId());
        }

        long currentMemberCount = teamMemberRepository.countByTeam_Id(teamId);
        if (currentMemberCount >= team.getTournament().getTeamSize()) {
            throw new TeamFullException(teamId);
        }

        teamMemberRepository.save(TeamMember.create(team, application.getUser()));

        application.approve();

        return toRecruitmentApplicationResponse(application);
    }

    @Transactional
    public RecruitmentApplicationResponse rejectApplication(Long currentUserId, Long teamId, Long applicationId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(currentUserId)) {
            throw new NotTeamOwnerException(currentUserId, teamId);
        }

        RecruitmentApplication application = recruitmentApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RecruitmentApplicationNotFoundException(teamId, applicationId));

        if (!application.getTeam().getId().equals(teamId)) {
            throw new RecruitmentApplicationNotFoundException(teamId, applicationId);
        }

        if (application.getStatus() != RecruitmentApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException(applicationId);
        }

        application.reject();

        return toRecruitmentApplicationResponse(application);
    }

    private RecruitmentApplicationResponse toRecruitmentApplicationResponse(RecruitmentApplication application) {
        return new RecruitmentApplicationResponse(
                application.getId(),
                application.getTeam().getId(),
                application.getUser().getId(),
                application.getUser().getName(),
                application.getMessage(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
