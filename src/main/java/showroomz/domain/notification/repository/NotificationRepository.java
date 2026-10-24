package showroomz.domain.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.type.NotificationRecipientType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByDedupKey(String dedupKey);

    boolean existsByDedupKey(String dedupKey);

    @Query("SELECT n FROM Notification n WHERE n.recipientType = :type AND n.recipientId = :recipientId "
            + "AND n.createdAt >= :since AND (:unreadOnly = false OR n.readAt IS NULL) "
            + "ORDER BY n.createdAt DESC, n.id DESC")
    Page<Notification> findInbox(@Param("type") NotificationRecipientType type, @Param("recipientId") Long recipientId,
                                 @Param("since") LocalDateTime since, @Param("unreadOnly") boolean unreadOnly,
                                 Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientType = :type AND n.recipientId = :recipientId "
            + "AND n.createdAt >= :since AND n.readAt IS NULL")
    long countUnread(@Param("type") NotificationRecipientType type, @Param("recipientId") Long recipientId,
                     @Param("since") LocalDateTime since);

    /** 소비자 종 배지 — 목록에 마지막으로 들어간 뒤 새로 온 수(C11 · 디자인 시스템). */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientType = showroomz.domain.notification.type.NotificationRecipientType.USER "
            + "AND n.recipientId = :userId AND n.createdAt > :after AND n.createdAt >= :since")
    long countUserBadge(@Param("userId") Long userId, @Param("after") LocalDateTime after,
                        @Param("since") LocalDateTime since);

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.recipientType = :type AND n.recipientId = :recipientId")
    Optional<Notification> findMine(@Param("id") Long id, @Param("type") NotificationRecipientType type,
                                    @Param("recipientId") Long recipientId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientType = :type AND n.recipientId = :recipientId "
            + "AND n.readAt IS NULL")
    int markAllRead(@Param("type") NotificationRecipientType type, @Param("recipientId") Long recipientId,
                    @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.recipientType = :type AND n.createdAt < :before")
    int deleteExpired(@Param("type") NotificationRecipientType type, @Param("before") LocalDateTime before);
}
