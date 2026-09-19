package com.sharp.repository;

import com.sharp.entity.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    @Query("SELECT e FROM EmailAccount e WHERE " +
            "(:emailType IS NULL OR :emailType = '' " +
            " OR (:emailType = 'other' AND e.emailType NOT IN ('gmail', '012e', 'outlook')) " +
            " OR (:emailType <> 'other' AND e.emailType = :emailType)) AND " +
            "(:keyword IS NULL OR :keyword = '' OR e.email LIKE %:keyword% OR e.recoveryEmail LIKE %:keyword% OR e.uuid LIKE %:keyword%) " +
            "ORDER BY e.id DESC")
    Page<EmailAccount> search(@Param("emailType") String emailType,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
