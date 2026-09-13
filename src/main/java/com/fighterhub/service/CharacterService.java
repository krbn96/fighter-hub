package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fighterhub.entity.Character;
import com.fighterhub.repository.CharacterRepository;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    public List<Character> findAll() {
        return characterRepository.findAll();
    }
}