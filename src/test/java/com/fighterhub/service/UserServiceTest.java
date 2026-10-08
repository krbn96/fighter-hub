package com.fighterhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fighterhub.dto.UserCharacterRequest;
import com.fighterhub.dto.UserCharacterResponse;
import com.fighterhub.dto.UserCreateRequest;
import com.fighterhub.dto.UserCreateResponse;
import com.fighterhub.dto.UserMeResponse;
import com.fighterhub.dto.UserPublicResponse;
import com.fighterhub.dto.UserUpdateRequest;
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

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "MASTER", 1800);
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
        assertEquals("MASTER", capturedUser.getRank1());
        assertEquals(1800, capturedUser.getMr1());
    }

    @Test
    void createUser_emailが既に登録されている場合_EmailAlreadyExistsExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "MASTER", 1800);
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

        UserCharacterRequest characterRequest1 = new UserCharacterRequest(1L, "MASTER", 1800);
        UserCharacterRequest characterRequest2 = new UserCharacterRequest(1L, "DIAMOND", null);
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

        UserCharacterRequest characterRequest = new UserCharacterRequest(999L, "MASTER", 1800);
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
    void createUser_rankとmrの許可された組み合わせの場合_正常に登録される() {

        UserCharacterRequest characterRequest1 = new UserCharacterRequest(1L, "GOLD", null);
        UserCharacterRequest characterRequest2 = new UserCharacterRequest(2L, "MASTER", null);
        UserCharacterRequest characterRequest3 = new UserCharacterRequest(3L, "MASTER", 1600);
        UserCreateRequest request = new UserCreateRequest(
                "user@example.com",
                "password123",
                "Ryu",
                List.of(characterRequest1, characterRequest2, characterRequest3),
                LocalTime.of(18, 0),
                LocalTime.of(23, 30),
                "Hello, world!"
        );

        Character character1 = new Character(1L, "Ryu");
        Character character2 = new Character(2L, "Ken");
        Character character3 = new Character(3L, "Chun-Li");

        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character1));
        when(characterRepository.findById(2L)).thenReturn(Optional.of(character2));
        when(characterRepository.findById(3L)).thenReturn(Optional.of(character3));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        User savedUser = mock(User.class);
        when(savedUser.getId()).thenReturn(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserCreateResponse response = userService.createUser(request);

        assertEquals(1L, response.id());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();
        assertEquals("GOLD", capturedUser.getRank1());
        assertEquals(null, capturedUser.getMr1());
        assertEquals("MASTER", capturedUser.getRank2());
        assertEquals(null, capturedUser.getMr2());
        assertEquals("MASTER", capturedUser.getRank3());
        assertEquals(1600, capturedUser.getMr3());
    }

    @Test
    void createUser_rankがMASTER以外でmrが指定された場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "GOLD", 1600);
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

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("mr must not be specified unless rank is MASTER. rank=GOLD", exception.getMessage());

        verify(characterRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rankが許可値以外の場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "UNKNOWN", null);
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

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("Invalid rank specified: UNKNOWN", exception.getMessage());

        verify(characterRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rankがMASTERでmrが負の値の場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "MASTER", -1);
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

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("mr must be 0 or greater. mr=-1", exception.getMessage());

        verify(characterRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rankが空文字の場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "", null);
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

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("Invalid rank specified: ", exception.getMessage());

        verify(characterRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rankがnullの場合_InvalidRequestExceptionを投げる() {

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, null, null);
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

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.createUser(request));

        assertEquals("Invalid rank specified: null", exception.getMessage());

        verify(characterRepository, never()).findById(any());
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
        when(user.getDiscordUsername()).thenReturn("testuser");
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
        assertEquals("testuser", response.discordUsername());
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

    // ==== updateUser ====

    private static UserUpdateRequest allUndefined() {
        return new UserUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined());
    }

    @Test
    void updateUser_該当する有効ユーザーが存在しない場合_UserNotFoundExceptionを投げる() {

        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.updateUser(999L, allUndefined()));

        assertEquals("User not found. id=999", exception.getMessage());
    }

    @Test
    void updateUser_全項目未指定の場合_Userを変更せずCharacterRepositoryへアクセスしない() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserMeResponse response = userService.updateUser(1L, allUndefined());

        assertNotNull(response);

        verify(user, never()).updateName(any());
        verify(user, never()).updateCharacters(any());
        verify(user, never()).updatePlayTimeStart(any());
        verify(user, never()).updatePlayTimeEnd(any());
        verify(user, never()).updateMessage(any());
        verify(user, never()).updateXId(any());
        verify(user, never()).linkDiscordAccount(any(), any());
        verify(user, never()).unlinkDiscordAccount();
        verify(characterRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_nameだけ指定された場合_nameだけ更新する() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.of("New Name"), JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.undefined());

        UserMeResponse response = userService.updateUser(1L, request);

        assertNotNull(response);
        verify(user).updateName("New Name");
        verify(user, never()).updateCharacters(any());
        verify(user, never()).updatePlayTimeStart(any());
        verify(user, never()).updatePlayTimeEnd(any());
        verify(user, never()).updateMessage(any());
        verify(user, never()).updateXId(any());
        verify(user, never()).linkDiscordAccount(any(), any());
        verify(user, never()).unlinkDiscordAccount();
        verify(characterRepository, never()).findById(any());
    }

    @Test
    void updateUser_messageが明示的nullの場合_messageをnullで更新する() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(),
                JsonNullable.undefined(), JsonNullable.of(null));

        userService.updateUser(1L, request);

        verify(user).updateMessage(null);
    }

    @Test
    void updateUser_playTimeStartが明示的nullの場合_nullで更新する() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(null),
                JsonNullable.undefined(), JsonNullable.undefined());

        userService.updateUser(1L, request);

        verify(user).updatePlayTimeStart(null);
        verify(user, never()).updatePlayTimeEnd(any());
    }

    @Test
    void updateUser_xId及びdiscordIdはrequestに存在せずPATCHでは更新されない() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        userService.updateUser(1L, allUndefined());

        verify(user, never()).updateXId(any());
        verify(user, never()).linkDiscordAccount(any(), any());
        verify(user, never()).unlinkDiscordAccount();
    }

    @Test
    void updateUser_charactersが1件指定された場合_CharacterAssignmentへ変換して全置換を委譲する() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        Character character = new Character(1L, "Ryu");
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "MASTER", 1600);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        UserMeResponse response = userService.updateUser(1L, request);

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User.CharacterAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(user).updateCharacters(captor.capture());

        List<User.CharacterAssignment> assignments = captor.getValue();
        assertEquals(1, assignments.size());
        assertEquals(character, assignments.get(0).character());
        assertEquals("MASTER", assignments.get(0).rank());
        assertEquals(1600, assignments.get(0).mr());
    }

    @Test
    void updateUser_charactersに同じcharacterIdが複数指定された場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserCharacterRequest characterRequest1 = new UserCharacterRequest(1L, "MASTER", 1600);
        UserCharacterRequest characterRequest2 = new UserCharacterRequest(1L, "DIAMOND", null);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest1, characterRequest2)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("Duplicate character_id specified: 1", exception.getMessage());
        verify(characterRepository, never()).findById(any());
        verify(user, never()).updateCharacters(any());
    }

    @Test
    void updateUser_指定したcharacterIdが存在しない場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(999L)).thenReturn(Optional.empty());

        UserCharacterRequest characterRequest = new UserCharacterRequest(999L, "MASTER", 1600);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("Character not found. character_id=999", exception.getMessage());
        verify(user, never()).updateCharacters(any());
    }

    @Test
    void updateUser_rankとmrの許可された組み合わせの場合_正常に更新する() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        Character character1 = new Character(1L, "Ryu");
        Character character2 = new Character(2L, "Ken");
        Character character3 = new Character(3L, "Chun-Li");
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character1));
        when(characterRepository.findById(2L)).thenReturn(Optional.of(character2));
        when(characterRepository.findById(3L)).thenReturn(Optional.of(character3));

        UserCharacterRequest characterRequest1 = new UserCharacterRequest(1L, "GOLD", null);
        UserCharacterRequest characterRequest2 = new UserCharacterRequest(2L, "MASTER", null);
        UserCharacterRequest characterRequest3 = new UserCharacterRequest(3L, "MASTER", 1600);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest1, characterRequest2, characterRequest3)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        UserMeResponse response = userService.updateUser(1L, request);

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User.CharacterAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(user).updateCharacters(captor.capture());

        List<User.CharacterAssignment> assignments = captor.getValue();
        assertEquals(3, assignments.size());
        assertEquals("GOLD", assignments.get(0).rank());
        assertEquals(null, assignments.get(0).mr());
        assertEquals("MASTER", assignments.get(1).rank());
        assertEquals(null, assignments.get(1).mr());
        assertEquals("MASTER", assignments.get(2).rank());
        assertEquals(1600, assignments.get(2).mr());
    }

    @Test
    void updateUser_rankがMASTER以外でmrが指定された場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "GOLD", 1600);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("mr must not be specified unless rank is MASTER. rank=GOLD", exception.getMessage());
        verify(characterRepository, never()).findById(any());
        verify(user, never()).updateCharacters(any());
    }

    @Test
    void updateUser_rankが許可値以外の場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "UNKNOWN", null);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("Invalid rank specified: UNKNOWN", exception.getMessage());
        verify(characterRepository, never()).findById(any());
        verify(user, never()).updateCharacters(any());
    }

    @Test
    void updateUser_rankがMASTERでmrが負の値の場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "MASTER", -1);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("mr must be 0 or greater. mr=-1", exception.getMessage());
        verify(characterRepository, never()).findById(any());
        verify(user, never()).updateCharacters(any());
    }

    @Test
    void updateUser_rankが空文字の場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, "", null);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("Invalid rank specified: ", exception.getMessage());
        verify(characterRepository, never()).findById(any());
        verify(user, never()).updateCharacters(any());
    }

    @Test
    void updateUser_rankがnullの場合_InvalidRequestExceptionを投げる() {

        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserCharacterRequest characterRequest = new UserCharacterRequest(1L, null, null);
        UserUpdateRequest request = new UserUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(List.of(characterRequest)),
                JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> userService.updateUser(1L, request));

        assertEquals("Invalid rank specified: null", exception.getMessage());
        verify(characterRepository, never()).findById(any());
        verify(user, never()).updateCharacters(any());
    }

    // ==== findMe ====

    @Test
    void findMe_有効なuserIdの場合_emailを含むUserMeResponseを返す() {

        Character character1 = new Character(1L, "Ryu");
        Character character3 = new Character(3L, "Chun-Li");

        LocalTime playTimeStart = LocalTime.of(20, 0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Test User");
        when(user.getCharacter1()).thenReturn(character1);
        when(user.getRank1()).thenReturn("MASTER");
        when(user.getMr1()).thenReturn(1600);
        when(user.getCharacter2()).thenReturn(null);
        when(user.getCharacter3()).thenReturn(character3);
        when(user.getRank3()).thenReturn("PLATINUM");
        when(user.getMr3()).thenReturn(1200);
        when(user.getCharacter4()).thenReturn(null);
        when(user.getPlayTimeStart()).thenReturn(playTimeStart);
        when(user.getPlayTimeEnd()).thenReturn(null);
        when(user.getMessage()).thenReturn(null);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getXId()).thenReturn(null);
        when(user.getDiscordUsername()).thenReturn(null);
        when(user.getCreatedAt()).thenReturn(createdAt);
        when(user.getUpdatedAt()).thenReturn(updatedAt);

        when(userRepository.findByIdAndDeleteFlagFalse(1L)).thenReturn(Optional.of(user));

        UserMeResponse response = userService.findMe(1L);

        // emailが含まれること
        assertEquals("user@example.com", response.email());

        // slot順でUserCharacterResponseへ変換され、character2/4(null)はスキップされること
        assertEquals(2, response.characters().size());
        assertEquals(1L, response.characters().get(0).characterId());
        assertEquals("MASTER", response.characters().get(0).rank());
        assertEquals(1600, response.characters().get(0).mr());
        assertEquals(3L, response.characters().get(1).characterId());
        assertEquals("PLATINUM", response.characters().get(1).rank());
        assertEquals(1200, response.characters().get(1).mr());

        // nullableなプロフィール項目がnullでも正しく返せること
        assertEquals(playTimeStart, response.playTimeStart());
        assertEquals(null, response.playTimeEnd());
        assertEquals(null, response.message());
        assertEquals(null, response.xId());
        assertEquals(null, response.discordUsername());

        assertEquals(1L, response.id());
        assertEquals("Test User", response.name());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void findMe_存在しないuserIdの場合_UserNotFoundExceptionを投げる() {

        when(userRepository.findByIdAndDeleteFlagFalse(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.findMe(999L));

        assertEquals("User not found. id=999", exception.getMessage());
    }

    @Test
    void findMe_UserMeResponseにpasswordHashとdeleteFlagが含まれない設計であること() {
        // UserMeResponseレコード自体がpasswordHash/deleteFlagフィールドを持たないため、
        // toUserMeResponse()経由でこれらがレスポンスへ露出することは構造上あり得ない。
        // ここではrecordの構成要素を確認することでその設計を担保する。

        List<String> componentNames = java.util.Arrays.stream(UserMeResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertTrue(componentNames.contains("email"));
        assertTrue(!componentNames.contains("passwordHash"));
        assertTrue(!componentNames.contains("deleteFlag"));
    }

    // ==== linkDiscordAccount ====

    @Test
    void linkDiscordAccount_未連携の場合_現在Userへ新規に連携する() {
        User currentUser = mock(User.class);
        when(userRepository.findByDiscordId("discord-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(1L))
                .thenReturn(Optional.of(currentUser));

        userService.linkDiscordAccount(1L, "discord-1", "testuser");

        verify(currentUser).linkDiscordAccount("discord-1", "testuser");
        verify(currentUser, never()).unlinkDiscordAccount();
        verify(userRepository, times(1)).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void linkDiscordAccount_自分自身に既に連携済みの場合_discordUsernameを更新するだけで解除処理は呼ばれない() {
        User currentUser = mock(User.class);
        when(currentUser.getId()).thenReturn(1L);
        when(userRepository.findByDiscordId("discord-1"))
                .thenReturn(Optional.of(currentUser));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(1L))
                .thenReturn(Optional.of(currentUser));

        userService.linkDiscordAccount(1L, "discord-1", "new-username");

        verify(currentUser).linkDiscordAccount("discord-1", "new-username");
        verify(currentUser, never()).unlinkDiscordAccount();
        // 対象が1User(自分自身)のみなので、ロック取得も1回だけで良い。
        verify(userRepository, times(1)).findByIdAndDeleteFlagFalseForUpdate(any());
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    // 正式business rule: 旧User/現Userのロック順序を常にID昇順に固定し、デッドロックを防ぐ。
    // currentUserId(2)が旧User(1)より大きい場合でも、ID昇順(1→2)でロックされることを確認する。
    // 旧User(既存保持者)のロックはfindByIdForUpdate、現Userのロックは
    // findByIdAndDeleteFlagFalseForUpdateと使い分けられることも併せて確認する。
    @Test
    void linkDiscordAccount_別ユーザーに連携済みの場合_旧Userを解除し現在Userへ付け替えID昇順でロックする() {
        User oldOwner = mock(User.class);
        when(oldOwner.getId()).thenReturn(1L);
        when(oldOwner.getDiscordId()).thenReturn("discord-1");

        User currentUser = mock(User.class);

        when(userRepository.findByDiscordId("discord-1"))
                .thenReturn(Optional.of(oldOwner));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(oldOwner));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L))
                .thenReturn(Optional.of(currentUser));

        userService.linkDiscordAccount(2L, "discord-1", "new-username");

        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(userRepository).findByIdAndDeleteFlagFalseForUpdate(2L);

        verify(oldOwner).unlinkDiscordAccount();
        verify(currentUser).linkDiscordAccount("discord-1", "new-username");
    }

    @Test
    void linkDiscordAccount_旧Userが既に別のDiscordIdへ変わっていた場合_解除処理をスキップする() {
        User oldOwner = mock(User.class);
        when(oldOwner.getId()).thenReturn(2L);
        // lock取得後の再確認時点では、別Transactionにより既に別のdiscordIdへ変わっている
        // (= もはやこのdiscordIdの実際の保持者ではない)ケース。
        when(oldOwner.getDiscordId()).thenReturn("already-changed");

        User currentUser = mock(User.class);

        when(userRepository.findByDiscordId("discord-1"))
                .thenReturn(Optional.of(oldOwner));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(1L))
                .thenReturn(Optional.of(currentUser));
        when(userRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(oldOwner));

        userService.linkDiscordAccount(1L, "discord-1", "new-username");

        verify(oldOwner, never()).unlinkDiscordAccount();
        verify(currentUser).linkDiscordAccount("discord-1", "new-username");
    }

    // Day 7品質調査で指摘された不整合への対応: 既存保持者が論理削除済み(delete_flag=true)でも
    // findByDiscordIdで検出し、findByIdForUpdateでロックして解除できることを確認する。
    @Test
    void linkDiscordAccount_既存保持者が論理削除済みの場合でも検出し解除してから現在Userへ連携する() {
        User deletedOldOwner = mock(User.class);
        when(deletedOldOwner.getId()).thenReturn(1L);
        when(deletedOldOwner.getDiscordId()).thenReturn("discord-1");

        User currentUser = mock(User.class);

        when(userRepository.findByDiscordId("discord-1"))
                .thenReturn(Optional.of(deletedOldOwner));
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(deletedOldOwner));
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(2L))
                .thenReturn(Optional.of(currentUser));

        userService.linkDiscordAccount(2L, "discord-1", "new-username");

        verify(deletedOldOwner).unlinkDiscordAccount();
        verify(currentUser).linkDiscordAccount("discord-1", "new-username");
    }

    // 論理削除ユーザー自身への連携は許可しない: currentUserId自体がすでに論理削除されている場合、
    // findByIdAndDeleteFlagFalseForUpdateが空を返しUserNotFoundExceptionになる
    // (既存のアクティブUser限定の仕様を、既存保持者検索の変更後も維持する)。
    @Test
    void linkDiscordAccount_現在User自身が論理削除済みの場合_UserNotFoundExceptionを投げ連携しない() {
        when(userRepository.findByDiscordId("discord-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(1L))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.linkDiscordAccount(1L, "discord-1", "new-username"));

        verify(userRepository, never()).findByIdForUpdate(any());
    }

    // ==== unlinkDiscordAccount ====

    @Test
    void unlinkDiscordAccount_存在するUserの場合_連携を解除する() {
        User user = mock(User.class);
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(1L)).thenReturn(Optional.of(user));

        userService.unlinkDiscordAccount(1L);

        verify(user).unlinkDiscordAccount();
    }

    @Test
    void unlinkDiscordAccount_存在しないUserの場合_UserNotFoundExceptionを投げる() {
        when(userRepository.findByIdAndDeleteFlagFalseForUpdate(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.unlinkDiscordAccount(999L));

        assertEquals("User not found. id=999", exception.getMessage());
    }
}
