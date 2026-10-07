package com.fighterhub.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
import com.fighterhub.entity.Rank;
import com.fighterhub.entity.User;
import com.fighterhub.exception.EmailAlreadyExistsException;
import com.fighterhub.exception.InvalidRequestException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.CharacterRepository;
import com.fighterhub.repository.UserRepository;

@Service
public class UserService {

    private static final String MASTER_RANK = "MASTER";

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

        // xId/discordIdはPATCH /api/users/meからの直接編集を提供しない(OAuth連携専用の
        // 更新経路(linkDiscordAccount/unlinkDiscordAccount)のみで更新される)。

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

            validateRankAndMr(characterRequest.rank(), characterRequest.mr());
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

    // 正式business rule(frontendのRANK_OPTIONS/MASTERのみMR入力可能という仕様をbackendでも強制する):
    // - rankはRank enum(8値)のいずれかであること(null/blankは@NotBlankで別途拒否される)
    // - rankがMASTER以外の場合、mrはnull以外を指定できない
    // - rankがMASTERの場合、mrはnullを許可し、null以外なら0以上であること
    // - mrの上限はInteger型の範囲に委ね、独自の上限チェックは追加しない
    private void validateRankAndMr(String rank, Integer mr) {
        if (!Rank.isValid(rank)) {
            throw new InvalidRequestException("Invalid rank specified: " + rank);
        }

        if (!MASTER_RANK.equals(rank)) {
            if (mr != null) {
                throw new InvalidRequestException(
                        "mr must not be specified unless rank is MASTER. rank=" + rank);
            }
            return;
        }

        if (mr != null && mr < 0) {
            throw new InvalidRequestException("mr must be 0 or greater. mr=" + mr);
        }
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
                user.getDiscordUsername(),
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
                user.getDiscordUsername(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    // Discordアカウント連携(新規/自分自身への再連携/他Userからの付け替え)。
    //
    // lock取得順序: 対象Discord IDを現在連携しているUser(いれば)と現在Userの両方を、
    // ID昇順で固定した順序でPESSIMISTIC_WRITEロックする(findByDiscordIdAndDeleteFlagFalseは
    // ロックを取得しない事前検索であり、実際の更新は必ずこのID昇順ロックの後に行う)。
    // 複数のDiscordアカウント連携が同時に実行されても、どのTransactionも常に同じ順序で
    // ロックを取得するため、旧User/現Userのロック順序が不定になる循環待ち(deadlock)は
    // 構造的に起こり得ない(Tournament/RecruitmentApplicationの既存ロック順序統一と同じ考え方)。
    @Transactional
    public void linkDiscordAccount(Long currentUserId, String discordId, String discordUsername) {
        Optional<User> existingHolder = userRepository.findByDiscordIdAndDeleteFlagFalse(discordId);

        SortedSet<Long> idsToLock = new TreeSet<>();
        idsToLock.add(currentUserId);
        existingHolder.ifPresent(holder -> idsToLock.add(holder.getId()));

        Map<Long, User> lockedUsers = new HashMap<>();
        for (Long id : idsToLock) {
            User lockedUser = userRepository.findByIdAndDeleteFlagFalseForUpdate(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
            lockedUsers.put(id, lockedUser);
        }

        if (existingHolder.isPresent() && !existingHolder.get().getId().equals(currentUserId)) {
            User oldOwner = lockedUsers.get(existingHolder.get().getId());
            // lock取得後に再確認する: この間に別Transactionが既にoldOwnerの連携を
            // 解除・変更している可能性があるため、実際にまだ同じdiscordIdを保持している
            // 場合のみ解除する。
            if (discordId.equals(oldOwner.getDiscordId())) {
                oldOwner.unlinkDiscordAccount();
            }
        }

        User currentUser = lockedUsers.get(currentUserId);
        currentUser.linkDiscordAccount(discordId, discordUsername);
    }

    // 未連携状態で呼ばれても成功扱いとする冪等な解除(discordId/discordUsernameを
    // 同時にクリアする)。
    @Transactional
    public void unlinkDiscordAccount(Long userId) {
        User user = userRepository.findByIdAndDeleteFlagFalseForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.unlinkDiscordAccount();
    }
}
