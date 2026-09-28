package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.TournamentCreateRequest;
import com.fighterhub.dto.TournamentResponse;
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
        User user = userRepository.findByIdAndDeleteFlagFalse(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));

        if (user.getRole() != UserRole.ADMIN) {
            throw new NotAdminException(currentUserId);
        }

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
