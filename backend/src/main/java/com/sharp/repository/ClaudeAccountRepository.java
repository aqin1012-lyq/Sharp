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

    /** 五字段完全一致的记录数（null 安全：两边都为 null 视为相等）。用于查重。 */
    @Query("SELECT COUNT(c) FROM ClaudeAccount c WHERE " +
            "((:claudeAccount IS NULL AND c.claudeAccount IS NULL) OR c.claudeAccount = :claudeAccount) AND " +
            "((:email IS NULL AND c.email IS NULL) OR c.email = :email) AND " +
            "((:emailPassword IS NULL AND c.emailPassword IS NULL) OR c.emailPassword = :emailPassword) AND " +
            "((:sk IS NULL AND c.sk IS NULL) OR c.sk = :sk) AND " +
            "((:accountId IS NULL AND c.accountId IS NULL) OR c.accountId = :accountId)")
    long countMatching(@Param("claudeAccount") String claudeAccount,
                       @Param("email") String email,
                       @Param("emailPassword") String emailPassword,
                       @Param("sk") String sk,
                       @Param("accountId") String accountId);
}
