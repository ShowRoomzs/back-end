package showroomz.domain.notification.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.notification.entity.NotificationPushOutbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationPushOutboxRepository extends JpaRepository<NotificationPushOutbox, Long> {

    Optional<NotificationPushOutbox> findByNotificationId(Long notificationId);

    @Query("SELECT o FROM NotificationPushOutbox o WHERE o.sentAt IS NULL AND o.sendAfter <= :now ORDER BY o.sendAfter ASC, o.id ASC")
    List<NotificationPushOutbox> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM NotificationPushOutbox o WHERE o.sentAt IS NOT NULL AND o.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
