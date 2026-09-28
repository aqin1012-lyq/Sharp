package com.sharp.repository;

import com.sharp.entity.AppleCodeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppleCodeLogRepository extends JpaRepository<AppleCodeLog, Long> {

    @Query("SELECT l FROM AppleCodeLog l WHERE " +
            "(:type IS NULL OR :type = '' OR l.type = :type) AND " +
            "(:success IS NULL OR l.success = :success) AND " +
            "(:keyword IS NULL OR :keyword = '' OR l.entryEmail LIKE %:keyword% " +
            " OR l.forwardEmail LIKE %:keyword% OR l.message LIKE %:keyword%) " +
            "ORDER BY l.id DESC")
    Page<AppleCodeLog> search(@Param("type") String type,
                              @Param("success") Boolean success,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
