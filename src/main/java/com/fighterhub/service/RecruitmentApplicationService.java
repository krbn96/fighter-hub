package com.fighterhub.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.User;
import com.fighterhub.exception.CannotApplyToOwnTeamException;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
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
