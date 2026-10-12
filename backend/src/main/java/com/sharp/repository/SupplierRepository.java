package com.sharp.repository;

import com.sharp.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Query("SELECT s FROM Supplier s WHERE " +
            "(:keyword IS NULL OR :keyword = '' " +
            " OR s.ip LIKE %:keyword% OR s.username LIKE %:keyword% OR s.region LIKE %:keyword%) " +
            "ORDER BY s.id DESC")
    Page<Supplier> search(@Param("keyword") String keyword, Pageable pageable);

    /** 六字段完全一致的记录数（null 安全：两边都为 null 视为相等）。用于查重。 */
    @Query("SELECT COUNT(s) FROM Supplier s WHERE " +
            "((:ip IS NULL AND s.ip IS NULL) OR s.ip = :ip) AND " +
            "((:port IS NULL AND s.port IS NULL) OR s.port = :port) AND " +
            "((:username IS NULL AND s.username IS NULL) OR s.username = :username) AND " +
            "((:password IS NULL AND s.password IS NULL) OR s.password = :password) AND " +
            "((:region IS NULL AND s.region IS NULL) OR s.region = :region) AND " +
            "((:expireTime IS NULL AND s.expireTime IS NULL) OR s.expireTime = :expireTime)")
    long countMatching(@Param("ip") String ip,
                       @Param("port") String port,
                       @Param("username") String username,
                       @Param("password") String password,
                       @Param("region") String region,
                       @Param("expireTime") String expireTime);
}
