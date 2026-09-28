package com.sharp.repository;

import com.sharp.entity.AppleHackerEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppleHackerEmailRepository extends JpaRepository<AppleHackerEmail, Long> {

    @Query("SELECT k FROM AppleHackerEmail k WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR k.hackerEmail LIKE %:keyword%) " +
            "ORDER BY k.id DESC")
    Page<AppleHackerEmail> search(@Param("keyword") String keyword, Pageable pageable);

    Optional<AppleHackerEmail> findByHideEmailId(Long hideEmailId);

    Optional<AppleHackerEmail> findByHackerEmail(String hackerEmail);

    /** 批量录入前一次性查出已占用的黑客邮箱 / 已绑定的隐藏邮箱，避免逐条查库。 */
    List<AppleHackerEmail> findByHackerEmailIn(Collection<String> hackerEmails);

    List<AppleHackerEmail> findByHideEmailIdIn(Collection<Long> hideEmailIds);
}
