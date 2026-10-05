package com.fighterhub.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;

    public TournamentService(TournamentRepository tournamentRepository, UserRepository userRepository) {
        this.tournamentRepository = tournamentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TournamentResponse> findAllTournaments() {
        return tournamentRepository.findAllByDeleteFlagFalse().stream()
                .map(this::toTournamentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentResponse findTournamentById(Long id) {
        Tournament tournament = tournamentRepository.findByIdAndDeleteFlagFalse(id)
                .orElseThrow(() -> new TournamentNotFoundException(id));

        return toTournamentResponse(tournament);
    }

    @Transactional
    public TournamentResponse createTournament(Long currentUserId, TournamentCreateRequest request) {
        validateAdmin(currentUserId);

        validateRecruitmentDeadlineBeforeStartAt(request.recruitmentDeadline(), request.startAt());

        Tournament tournament = Tournament.create(
                request.name(),
                request.teamSize(),
                request.startAt(),
                request.recruitmentDeadline(),
                request.maxPlayers()
        );
        Tournament savedTournament = tournamentRepository.save(tournament);

        return toTournamentResponse(savedTournament);
    }

    @Transactional
    public TournamentResponse updateTournament(Long currentUserId, Long id, TournamentUpdateRequest request) {
        validateAdmin(currentUserId);

        Tournament tournament = tournamentRepository.findByIdAndDeleteFlagFalse(id)
                .orElseThrow(() -> new TournamentNotFoundException(id));

        if (!request.name().isUndefined()) {
            tournament.updateName(request.name().get());
        }

        if (!request.teamSize().isUndefined()) {
            tournament.updateTeamSize(request.teamSize().get());
        }

        if (!request.startAt().isUndefined()) {
            tournament.updateStartAt(request.startAt().get());
        }

        if (!request.recruitmentDeadline().isUndefined()) {
            tournament.updateRecruitmentDeadline(request.recruitmentDeadline().get());
        }

        if (!request.maxPlayers().isUndefined()) {
            tournament.updateMaxPlayers(request.maxPlayers().get());
        }

        // PATCHでは「startAtだけ変更」「recruitmentDeadlineだけ変更」「両方変更」のいずれも
        // あり得るため、requestの生の値同士ではなく、更新適用後のTournamentの実効値
        // (既存値+更新値を反映した最終状態)で検証する。
        validateRecruitmentDeadlineBeforeStartAt(tournament.getRecruitmentDeadline(), tournament.getStartAt());

        // dirty checkingによるUPDATEはtransactionコミット時までflushされないため、
        // flushしないまま生成すると@UpdateTimestampが未反映のupdatedAtをレスポンスに含めてしまう。
        tournamentRepository.flush();

        return toTournamentResponse(tournament);
    }

    @Transactional
    public void deleteTournament(Long currentUserId, Long id) {
        validateAdmin(currentUserId);

        Tournament tournament = tournamentRepository.findByIdAndDeleteFlagFalse(id)
                .orElseThrow(() -> new TournamentNotFoundException(id));

        tournament.deactivate();
    }

    // ADMIN判定をcreate/update/deleteで共通化する。
    private void validateAdmin(Long currentUserId) {
        User user = userRepository.findByIdAndDeleteFlagFalse(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));

        if (user.getRole() != UserRole.ADMIN) {
            throw new NotAdminException(currentUserId);
        }
    }

    // 正式business rule: recruitmentDeadline < startAt(同時刻不可)。create/updateで共通化する。
    // updateTournament側は、更新適用後のTournamentの実効値(既存値+更新値を反映した最終状態)を
    // 渡して呼び出すことで、PATCHの部分更新(片方のフィールドだけ変更された場合)も正しく検証する。
    private void validateRecruitmentDeadlineBeforeStartAt(LocalDateTime recruitmentDeadline, LocalDateTime startAt) {
        if (!recruitmentDeadline.isBefore(startAt)) {
            throw new InvalidRequestException("recruitmentDeadline must be before startAt");
        }
    }

    private TournamentResponse toTournamentResponse(Tournament tournament) {
        return new TournamentResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getTeamSize(),
                tournament.getStartAt(),
                tournament.getRecruitmentDeadline(),
                tournament.getMaxPlayers(),
                tournament.getCreatedAt(),
                tournament.getUpdatedAt()
        );
    }
}
