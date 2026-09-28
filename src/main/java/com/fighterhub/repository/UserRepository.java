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

    // 同一Userによる複数Teamへの同時申請、および同一Tournament内の複数Teamへの
    // 同時承認を直列化するための行ロック付き取得。必ず@Transactional内から使用する。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleteFlag = false")
    Optional<User> findByIdAndDeleteFlagFalseForUpdate(@Param("id") Long id);
}
