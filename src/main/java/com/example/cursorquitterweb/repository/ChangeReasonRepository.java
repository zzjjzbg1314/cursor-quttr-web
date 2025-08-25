package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.ChangeReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 用户戒色改变理由数据访问接口
 */
@Repository
public interface ChangeReasonRepository extends JpaRepository<ChangeReason, UUID> {
    
    /**
     * 根据用户ID查找所有改变理由记录，按创建时间倒序排列
     * @param userId 用户ID
     * @return 改变理由记录列表
     */
    List<ChangeReason> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * 根据用户ID查找最近的一条改变理由记录
     * @param userId 用户ID
     * @return 最近的改变理由记录
     */
    @Query("SELECT cr FROM ChangeReason cr WHERE cr.userId = :userId ORDER BY cr.createdAt DESC")
    List<ChangeReason> findLatestByUserId(@Param("userId") UUID userId);
    
    /**
     * 统计用户改变理由记录数量
     * @param userId 用户ID
     * @return 记录数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 根据用户ID删除所有改变理由记录
     * @param userId 用户ID
     */
    void deleteByUserId(UUID userId);
}
