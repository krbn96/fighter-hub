package com.fighterhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.Tournament;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

}
