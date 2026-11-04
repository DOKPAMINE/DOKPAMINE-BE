package com.dokpamine.domain.user.repository;

import com.dokpamine.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByRankingOptInTrue();
}
