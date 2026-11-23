package com.exchange.matching.persistence.repository;

import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UserEntity> findAllByOrderByCreatedAtAsc();

    long countByRole(Role role);

    long countByStatus(AccountStatus status);

    long countByRoleAndStatus(Role role, AccountStatus status);
}
