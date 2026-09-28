package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.dto.TournamentUpdateRequest;
import com.fighterhub.entity.Tournament;
import com.fighterhub.entity.User;
import com.fighterhub.entity.UserRole;
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

        Tournament tournament = Tournament.create(
                request.name(),
                request.teamSize(),
                request.startAt(),
                request.maxPlayers(),
                request.status()
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

        if (!request.maxPlayers().isUndefined()) {
            tournament.updateMaxPlayers(request.maxPlayers().get());
        }

        if (!request.status().isUndefined()) {
            tournament.updateStatus(request.status().get());
        }

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

    private TournamentResponse toTournamentResponse(Tournament tournament) {
        return new TournamentResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getTeamSize(),
                tournament.getStartAt(),
                tournament.getMaxPlayers(),
                tournament.getStatus(),
                tournament.getCreatedAt(),
                tournament.getUpdatedAt()
        );
    }
}
