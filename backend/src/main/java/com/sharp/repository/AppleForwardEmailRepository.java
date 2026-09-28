package com.sharp.repository;

import com.sharp.entity.AppleForwardEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppleForwardEmailRepository extends JpaRepository<AppleForwardEmail, Long> {

    @Query("SELECT f FROM AppleForwardEmail f WHERE " +
            "(:appleAccountId IS NULL OR f.appleAccountId = :appleAccountId) AND " +
            "(:keyword IS NULL OR :keyword = '' OR f.forwardEmail LIKE %:keyword% OR f.note LIKE %:keyword%) " +
            "ORDER BY f.appleAccountId DESC, f.isCurrent DESC, f.id DESC")
    Page<AppleForwardEmail> search(@Param("appleAccountId") Long appleAccountId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    List<AppleForwardEmail> findByAppleAccountIdOrderByIsCurrentDescIdDesc(Long appleAccountId);

    Optional<AppleForwardEmail> findByAppleAccountIdAndForwardEmail(Long appleAccountId, String forwardEmail);

    long countByAppleAccountId(Long appleAccountId);

    /** 设为当前前先把同一 Apple ID 下的其余清零。 */
    @Modifying
    @Query("UPDATE AppleForwardEmail f SET f.isCurrent = false WHERE f.appleAccountId = :appleAccountId")
    void clearCurrent(@Param("appleAccountId") Long appleAccountId);
}
