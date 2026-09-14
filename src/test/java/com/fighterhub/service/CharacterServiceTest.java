package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fighterhub.dto.CharacterResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.exception.CharacterNotFoundException;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private CharacterRepository characterRepository;

    @InjectMocks
    private CharacterService characterService;

    @Test
    void findById_存在するIDを指定した場合_キャラクターを返す() {

        Character character = new Character(1L, "Ryu");

        when(characterRepository.findById(1L))
                .thenReturn(Optional.of(character));

        CharacterResponse response = characterService.findById(1L);

        assertEquals(character.getId(), response.id());
        assertEquals(character.getName(), response.name());
    }

    @Test
    void findById_存在しないIDを指定した場合_例外を投げる() {

        when(characterRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                CharacterNotFoundException.class,
                () -> characterService.findById(999L)
        );
    }

    @Test
    void findAll_キャラクターが存在する場合_一覧を返す() {

        Character character1 = new Character(1L, "Ryu");
        Character character2 = new Character(2L, "Ken");

        when(characterRepository.findAll())
                .thenReturn(List.of(character1, character2));

        List<CharacterResponse> responses = characterService.findAll();

        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).id());
        assertEquals("Ryu", responses.get(0).name());

        assertEquals(2L, responses.get(1).id());
        assertEquals("Ken", responses.get(1).name());
    }
}