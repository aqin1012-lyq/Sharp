package com.sharp.repository;

import com.sharp.entity.AppleHideEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppleHideEmailRepository extends JpaRepository<AppleHideEmail, Long> {

    @Query("SELECT h FROM AppleHideEmail h WHERE " +
            "(:appleAccountId IS NULL OR h.appleAccountId = :appleAccountId) AND " +
            "(:forwardEmailId IS NULL OR h.forwardEmailId = :forwardEmailId) AND " +
            "(:keyword IS NULL OR :keyword = '' OR h.hideEmail LIKE %:keyword%) " +
            "ORDER BY h.id DESC")
    Page<AppleHideEmail> search(@Param("appleAccountId") Long appleAccountId,
                                @Param("forwardEmailId") Long forwardEmailId,
                                @Param("keyword") String keyword,
                                Pageable pageable);

    Optional<AppleHideEmail> findByHideEmail(String hideEmail);

    /** 批量录入前一次性查出已存在的隐藏邮箱，避免逐条查库。 */
    List<AppleHideEmail> findByHideEmailIn(Collection<String> hideEmails);

    long countByAppleAccountId(Long appleAccountId);

    long countByForwardEmailId(Long forwardEmailId);
}
