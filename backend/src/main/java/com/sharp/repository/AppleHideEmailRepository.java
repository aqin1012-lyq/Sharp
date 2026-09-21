package com.sharp.repository;

import com.sharp.entity.AppleHideEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppleHideEmailRepository extends JpaRepository<AppleHideEmail, Long> {

    @Query("SELECT h FROM AppleHideEmail h WHERE " +
            "(:appleAccountId IS NULL OR h.appleAccountId = :appleAccountId) AND " +
            "(:keyword IS NULL OR :keyword = '' OR h.hideEmail LIKE %:keyword% " +
            " OR h.redirectEmail LIKE %:keyword% OR h.googleAliasEmail LIKE %:keyword%) " +
            "ORDER BY h.id DESC")
    Page<AppleHideEmail> search(@Param("appleAccountId") Long appleAccountId,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    Optional<AppleHideEmail> findByHideEmail(String hideEmail);

    long countByAppleAccountId(Long appleAccountId);
}
