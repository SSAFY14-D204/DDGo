package com.ssafy.DDGo.users.dao;

import com.ssafy.DDGo.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query(value = "SELECT COUNT(*) FROM users WHERE username = ?", nativeQuery = true)
    long countByUsernameIncludingDeleted(String username);

    @Query(value = "SELECT COUNT(*) FROM users WHERE email = ?", nativeQuery = true)
    long countByEmailIncludingDeleted(String email);

    @Query(value = "SELECT COUNT(*) FROM users WHERE nickname = ?", nativeQuery = true)
    long countByNicknameIncludingDeleted(String nickname);
}
