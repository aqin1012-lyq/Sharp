package com.sharp.repository;

import com.sharp.entity.ClaudeAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaudeAccountRepository extends JpaRepository<ClaudeAccount, Long> {

    @Query("SELECT c FROM ClaudeAccount c WHERE " +
            "(:keyword IS NULL OR :keyword = '' " +
            " OR c.claudeAccount LIKE %:keyword% OR c.email LIKE %:keyword% OR c.accountId LIKE %:keyword%) " +
            "ORDER BY c.id DESC")
    Page<ClaudeAccount> search(@Param("keyword") String keyword, Pageable pageable);
}
