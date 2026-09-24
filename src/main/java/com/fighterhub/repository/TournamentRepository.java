package com.fighterhub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.Tournament;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findByIdAndDeleteFlagFalse(Long id);
}
