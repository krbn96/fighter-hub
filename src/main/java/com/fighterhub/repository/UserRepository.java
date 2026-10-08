package com.fighterhub.repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fighterhub.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByIdAndDeleteFlagFalse(Long id);

    Optional<User> findByEmailAndDeleteFlagFalse(String email);

    // Discordアカウント連携時、指定したDiscord User IDを現在連携しているUserがいないかを
    // 確認するための非ロック検索。delete_flag条件を付けない: 論理削除後もdiscord_idが
    // クリアされずに残っている可能性があり(ユーザー退会時にunlinkDiscordAccountが
    // 呼ばれない限り)、delete_flag=falseだけを見ると論理削除Userの保持分を見逃し、
    // 再連携時にdiscord_idのUNIQUE制約違反を起こすため、論理削除Userも検出対象に含める。
    // 連携の実際の更新は必ずfindByIdAndDeleteFlagFalseForUpdate/findByIdForUpdateで
    // 行ロックを取得した上で行う(このメソッド自体はロックを取得しない)。
    Optional<User> findByDiscordId(String discordId);

    // 同一Userによる複数Teamへの同時申請、および同一Tournament内の複数Teamへの
    // 同時承認を直列化するための行ロック付き取得。必ず@Transactional内から使用する。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleteFlag = false")
    Optional<User> findByIdAndDeleteFlagFalseForUpdate(@Param("id") Long id);

    // Discordアカウント連携で、論理削除済みの既存discord_id保持者もロックして
    // 解除できるようにするための行ロック付き取得。delete_flag条件を付けない点のみ
    // findByIdAndDeleteFlagFalseForUpdateと異なる(linkDiscordAccount専用、
    // 連携操作の対象本人である現在Userのロックには引き続きdeleteFlagFalse版を使用する)。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
