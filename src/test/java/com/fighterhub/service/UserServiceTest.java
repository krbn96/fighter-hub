package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.entity.Character;
import com.fighterhub.entity.User;
import com.fighterhub.exception.EmailAlreadyExistsException;
import com.fighterhub.exception.InvalidRequestException;
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
}
