package com.sharp.repository;

import com.sharp.entity.AppleAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppleAccountRepository extends JpaRepository<AppleAccount, Long> {

    @Query("SELECT a FROM AppleAccount a WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR a.appleId LIKE %:keyword% OR a.note LIKE %:keyword%) " +
            "ORDER BY a.id DESC")
    Page<AppleAccount> search(@Param("keyword") String keyword, Pageable pageable);

    Optional<AppleAccount> findByAppleId(String appleId);
}
