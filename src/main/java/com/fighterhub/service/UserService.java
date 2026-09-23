package com.fighterhub.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            CharacterRepository characterRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserCreateResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        List<User.CharacterAssignment> characterAssignments = resolveCharacterAssignments(request.characters());

        String passwordHash = passwordEncoder.encode(request.password());

        User user = User.create(
                request.name(),
                request.email(),
                passwordHash,
                characterAssignments,
                request.playTimeStart(),
                request.playTimeEnd(),
                request.message()
        );

        User savedUser = userRepository.save(user);

        return new UserCreateResponse(savedUser.getId());
    }

    @Transactional(readOnly = true)
    public UserPublicResponse findById(Long id) {
        User user = userRepository.findByIdAndDeleteFlagFalse(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return toUserPublicResponse(user);
    }

    @Transactional(readOnly = true)
    public UserMeResponse findMe(Long userId) {
        User user = userRepository.findByIdAndDeleteFlagFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return toUserMeResponse(user);
    }

    @Transactional
    public UserMeResponse updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findByIdAndDeleteFlagFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!request.name().isUndefined()) {
            user.updateName(request.name().get());
        }

        if (!request.characters().isUndefined()) {
            List<User.CharacterAssignment> characterAssignments =
                    resolveCharacterAssignments(request.characters().get());
            user.updateCharacters(characterAssignments);
        }

        if (!request.playTimeStart().isUndefined()) {
            user.updatePlayTimeStart(request.playTimeStart().get());
        }

        if (!request.playTimeEnd().isUndefined()) {
            user.updatePlayTimeEnd(request.playTimeEnd().get());
        }

        if (!request.message().isUndefined()) {
            user.updateMessage(request.message().get());
        }

        if (!request.xId().isUndefined()) {
            user.updateXId(request.xId().get());
        }

        if (!request.discordId().isUndefined()) {
            user.updateDiscordId(request.discordId().get());
        }

        // dirty checkingによるUPDATEはtransactionコミット時までflushされないため、
        // flushしないまま生成すると@UpdateTimestampが未反映のupdatedAtをレスポンスに含めてしまう。
        // ここで明示的にflushし、DBへの反映後の値をレスポンスへ反映させる。
        userRepository.flush();

        return toUserMeResponse(user);
    }

    // characterId重複チェック・Character解決・CharacterAssignment変換をcreateUser/updateUserで共通化する。
    private List<User.CharacterAssignment> resolveCharacterAssignments(
            List<UserCharacterRequest> characterRequests) {

        Set<Long> characterIds = new HashSet<>();
        for (UserCharacterRequest characterRequest : characterRequests) {
            Long characterId = characterRequest.characterId();
            if (!characterIds.add(characterId)) {
                throw new InvalidRequestException(
                        "Duplicate character_id specified: " + characterId);
            }
        }

        List<User.CharacterAssignment> characterAssignments = new ArrayList<>();
        for (UserCharacterRequest characterRequest : characterRequests) {
            Long characterId = characterRequest.characterId();

            Character character = characterRepository.findById(characterId)
                    .orElseThrow(() -> new InvalidRequestException(
                            "Character not found. character_id=" + characterId));

            characterAssignments.add(new User.CharacterAssignment(
                    character,
                    characterRequest.rank(),
                    characterRequest.mr()
            ));
        }

        return characterAssignments;
    }

    private UserPublicResponse toUserPublicResponse(User user) {
        List<UserCharacterResponse> characters = new ArrayList<>();

        if (user.getCharacter1() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter1().getId(), user.getRank1(), user.getMr1()));
        }
        if (user.getCharacter2() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter2().getId(), user.getRank2(), user.getMr2()));
        }
        if (user.getCharacter3() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter3().getId(), user.getRank3(), user.getMr3()));
        }
        if (user.getCharacter4() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter4().getId(), user.getRank4(), user.getMr4()));
        }

        return new UserPublicResponse(
                user.getId(),
                user.getName(),
                characters,
                user.getPlayTimeStart(),
                user.getPlayTimeEnd(),
                user.getMessage(),
                user.getXId(),
                user.getDiscordId(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private UserMeResponse toUserMeResponse(User user) {
        List<UserCharacterResponse> characters = new ArrayList<>();

        if (user.getCharacter1() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter1().getId(), user.getRank1(), user.getMr1()));
        }
        if (user.getCharacter2() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter2().getId(), user.getRank2(), user.getMr2()));
        }
        if (user.getCharacter3() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter3().getId(), user.getRank3(), user.getMr3()));
        }
        if (user.getCharacter4() != null) {
            characters.add(new UserCharacterResponse(
                    user.getCharacter4().getId(), user.getRank4(), user.getMr4()));
        }

        return new UserMeResponse(
                user.getId(),
                user.getName(),
                characters,
                user.getPlayTimeStart(),
                user.getPlayTimeEnd(),
                user.getMessage(),
                user.getEmail(),
                user.getXId(),
                user.getDiscordId(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
