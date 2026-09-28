package com.fighterhub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.Tournament;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findByIdAndDeleteFlagFalse(Long id);

    List<Tournament> findAllByDeleteFlagFalse();
}
