package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.TournamentResponse;
import com.fighterhub.entity.Tournament;
import com.fighterhub.exception.TournamentNotFoundException;
import com.fighterhub.repository.TournamentRepository;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;

    public TournamentService(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
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
