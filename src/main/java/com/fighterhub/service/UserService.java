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
import com.fighterhub.dto.UserPublicResponse;
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

        List<UserCharacterRequest> characterRequests = request.characters();

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
}
