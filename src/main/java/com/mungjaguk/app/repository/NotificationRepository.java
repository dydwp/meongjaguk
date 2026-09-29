package com.mungjaguk.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mungjaguk.app.entity.Notification;

/** 알림 조회/읽음 처리 - 담당: 박용제 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 헤더 알림 창: 최신순 10개 */
    List<Notification> findTop10ByUserIdOrderByCreatedAtDescNotificationIdDesc(Long userId);

    /** 종 아이콘 배지: 안 읽은 알림 수 */
    long countByUserIdAndReadFalse(Long userId);

    /** 알림 창을 열면 전부 읽음 처리 */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
