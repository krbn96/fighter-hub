package com.fighterhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.Character;

public interface CharacterRepository extends JpaRepository<Character, Long> {

}