package com.dokpamine.domain.user.repository;

import com.dokpamine.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, UUID id);

    List<User> findAllByRankingOptInTrue();
}
