package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.post.entity.PostNotificationLog;

import java.util.List;

/**
 * 알림 이력 — <b>삭제 메서드를 두지 않는다.</b> §24-6이 영구 보존을 요구하므로 파기 배치도
 * 이 테이블은 건드리지 않는다.
 */
public interface PostNotificationLogRepository extends JpaRepository<PostNotificationLog, Long> {

    List<PostNotificationLog> findByPostIdOrderBySentAtDesc(Long postId);

    List<PostNotificationLog> findByCreatorIdOrderBySentAtDesc(Long creatorId);
}
