package com.fighterhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

}
