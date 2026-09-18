package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCharacterResponse;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.dto.UserPublicResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.User;
import com.fighterhub.exception.EmailAlreadyExistsException;
import com.fighterhub.exception.InvalidRequestException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_未登録のemailと存在するcharacterIdを指定した場合_登録済みUserのidを返す() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "Master", 1800);
        UserCreateRequest request = new UserCreateRequest(
                "user@example.com",
                "password123",
                "Ryu",
                List.of(characterRequest),
                LocalTime.of(18, 0),
                LocalTime.of(23, 30),
                "Hello, world!"
        );

        Character character = new Character(1L, "Ryu");

        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        User savedUser = mock(User.class);
        when(savedUser.getId()).thenReturn(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserCreateResponse response = userService.createUser(request);

        assertEquals(1L, response.id());

        verify(userRepository).existsByEmail("user@example.com");
        verify(characterRepository).findById(1L);
        verify(passwordEncoder, times(1)).encode("password123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();
        assertEquals("Ryu", capturedUser.getName());
        assertEquals("user@example.com", capturedUser.getEmail());
        assertEquals("hashed-password", capturedUser.getPasswordHash());
        assertNotEquals("password123", capturedUser.getPasswordHash());
        assertEquals(character, capturedUser.getCharacter1());
        assertEquals("Master", capturedUser.getRank1());
        assertEquals(1800, capturedUser.getMr1());
    }

    @Test
    void createUser_emailが既に登録されている場合_EmailAlreadyExistsExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "Master", 1800);
        UserCreateRequest request = new UserCreateRequest(
                "user@example.com",
                "password123",
                "Ryu",
                List.of(characterRequest),
                LocalTime.of(18, 0),
                LocalTime.of(23, 30),
                "Hello, world!"
        );

        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.createUser(request));

        verify(characterRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_charactersに同じcharacterIdが複数指定された場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest1 = new UserCharacterRequest(1L, "Master", 1800);
        UserCharacterRequest characterRequest2 = new UserCharacterRequest(1L, "Diamond", null);
        UserCreateRequest request = new UserCreateRequest(
                "user@example.com",
                "password123",
                "Ryu",
                List.of(characterRequest1, characterRequest2),
                LocalTime.of(18, 0),
                LocalTime.of(23, 30),
                "Hello, world!"
        );

        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("Duplicate character_id specified: 1", exception.getMessage());

        verify(characterRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_指定したcharacterIdが存在しない場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(999L, "Master", 1800);
        UserCreateRequest request = new UserCreateRequest(
                "user@example.com",
                "password123",
                "Ryu",
                List.of(characterRequest),
                LocalTime.of(18, 0),
                LocalTime.of(23, 30),
                "Hello, world!"
        );

        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(characterRepository.findById(999L)).thenReturn(Optional.empty());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("Character not found. character_id=999", exception.getMessage());

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void findById_deleteFlagFalseのUserが存在する場合_UserPublicResponseへ変換して返す() {

        Character character1 = new Character(1L, "Ryu");
        Character character2 = new Character(2L, "Ken");
        Character character3 = new Character(3L, "Chun-Li");
        Character character4 = new Character(4L, "Guile");

        LocalTime playTimeStart = LocalTime.of(20, 0);
        LocalTime playTimeEnd = LocalTime.of(23, 30);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Test User");
        when(user.getCharacter1()).thenReturn(character1);
        when(user.getRank1()).thenReturn("MASTER");
        when(user.getMr1()).thenReturn(1600);
        when(user.getCharacter2()).thenReturn(character2);
        when(user.getRank2()).thenReturn("DIAMOND");
        when(user.getMr2()).thenReturn(null);
        when(user.getCharacter3()).thenReturn(character3);
        when(user.getRank3()).thenReturn("PLATINUM");
        when(user.getMr3()).thenReturn(1200);
        when(user.getCharacter4()).thenReturn(character4);
        when(user.getRank4()).thenReturn("GOLD");
        when(user.getMr4()).thenReturn(900);
        when(user.getPlayTimeStart()).thenReturn(playTimeStart);
        when(user.getPlayTimeEnd()).thenReturn(playTimeEnd);
        when(user.getMessage()).thenReturn("よろしくお願いします");
        when(user.getXId()).thenReturn("testxid");
        when(user.getDiscordId()).thenReturn("test#1234");
        when(user.getCreatedAt()).thenReturn(createdAt);
        when(user.getUpdatedAt()).thenReturn(updatedAt);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserPublicResponse response = userService.findById(1L);

        assertEquals(1L, response.id());
        assertEquals("Test User", response.name());
        assertEquals(playTimeStart, response.playTimeStart());
        assertEquals(playTimeEnd, response.playTimeEnd());
        assertEquals("よろしくお願いします", response.message());
        assertEquals("testxid", response.xId());
        assertEquals("test#1234", response.discordId());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());

        assertEquals(4, response.characters().size());

        UserCharacterResponse slot1 = response.characters().get(0);
        assertEquals(1L, slot1.characterId());
        assertEquals("MASTER", slot1.rank());
        assertEquals(1600, slot1.mr());

        UserCharacterResponse slot2 = response.characters().get(1);
        assertEquals(2L, slot2.characterId());
        assertEquals("DIAMOND", slot2.rank());
        assertEquals(null, slot2.mr());

        UserCharacterResponse slot3 = response.characters().get(2);
        assertEquals(3L, slot3.characterId());
        assertEquals("PLATINUM", slot3.rank());
        assertEquals(1200, slot3.mr());

        UserCharacterResponse slot4 = response.characters().get(3);
        assertEquals(4L, slot4.characterId());
        assertEquals("GOLD", slot4.rank());
        assertEquals(900, slot4.mr());

        verify(userRepository).findByIdAndDeleteFlagFalse(1L);
    }

    @Test
    void findById_deleteFlagFalseのUserが存在しない場合_UserNotFoundExceptionを投げる() {

        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.findById(999L));

        assertEquals("User not found. id=999", exception.getMessage());
    }

    @Test
    void findById_character2とcharacter4がnullの場合_character1とcharacter3のみ順序維持して返す() {

        Character characterA = new Character(1L, "Ryu");
        Character characterC = new Character(3L, "Chun-Li");

        User user = mock(User.class);
        when(user.getCharacter1()).thenReturn(characterA);
        when(user.getRank1()).thenReturn("MASTER");
        when(user.getMr1()).thenReturn(1600);
        when(user.getCharacter2()).thenReturn(null);
        when(user.getCharacter3()).thenReturn(characterC);
        when(user.getRank3()).thenReturn("PLATINUM");
        when(user.getMr3()).thenReturn(1200);
        when(user.getCharacter4()).thenReturn(null);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserPublicResponse response = userService.findById(1L);

        assertEquals(2, response.characters().size());

        UserCharacterResponse first = response.characters().get(0);
        assertEquals(1L, first.characterId());
        assertEquals("MASTER", first.rank());
        assertEquals(1600, first.mr());

        UserCharacterResponse second = response.characters().get(1);
        assertEquals(3L, second.characterId());
        assertEquals("PLATINUM", second.rank());
        assertEquals(1200, second.mr());
    }

    @Test
    void findById_character1から4がすべてnullの場合_charactersが空Listになる() {

        User user = mock(User.class);
        when(user.getCharacter1()).thenReturn(null);
        when(user.getCharacter2()).thenReturn(null);
        when(user.getCharacter3()).thenReturn(null);
        when(user.getCharacter4()).thenReturn(null);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserPublicResponse response = userService.findById(1L);

        assertNotNull(response.characters());
        assertTrue(response.characters().isEmpty());
    }
}
