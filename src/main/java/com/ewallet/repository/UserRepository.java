package com.ewallet.repository;

import com.ewallet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Lookup by HMAC hash — avoids decrypting every row to find by email. */
    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :now, u.failedLoginAttempts = 0 WHERE u.id = :id")
    void recordSuccessfulLogin(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1, " +
           "u.locked = CASE WHEN u.failedLoginAttempts + 1 >= 5 THEN true ELSE u.locked END " +
           "WHERE u.id = :id")
    void incrementFailedLogins(@Param("id") UUID id);
}
