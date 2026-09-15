package com.fighterhub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.CharacterResponse;
import com.fighterhub.exception.CharacterNotFoundException;
import com.fighterhub.repository.CharacterRepository;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> findAll() {
        return characterRepository.findAll()
                .stream()
                .map(character -> new CharacterResponse(
                        character.getId(),
                        character.getName()
                ))
                .toList();
    }

        @Transactional(readOnly = true)
        public CharacterResponse findById(Long id) {
                return characterRepository.findById(id)
                        .map(character -> new CharacterResponse(
                                character.getId(),
                                character.getName()
                        ))
                        .orElseThrow(() -> new CharacterNotFoundException(id));
        }
}