package com.fighterhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
}
