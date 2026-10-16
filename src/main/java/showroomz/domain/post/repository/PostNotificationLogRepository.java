package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.vo.PostSanctionCounts;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 알림 이력 — <b>삭제 메서드를 두지 않는다.</b> §24-6이 영구 보존을 요구하므로 파기 배치도
 * 이 테이블은 건드리지 않는다.
 */
public interface PostNotificationLogRepository extends JpaRepository<PostNotificationLog, Long> {

    List<PostNotificationLog> findByPostIdOrderBySentAtDesc(Long postId);

    List<PostNotificationLog> findByCreatorIdOrderBySentAtDesc(Long creatorId);

    /** 상세 화면의 「작성자 알림 발송」 줄 — 같은 시각이면 먼저 적재된 것이 먼저다 */
    List<PostNotificationLog> findByPostIdOrderBySentAtAscIdAsc(Long postId);

    @Query("SELECT l.eventType, COUNT(l) FROM PostNotificationLog l " +
           "WHERE l.creatorId = :creatorId AND l.eventType IN :events GROUP BY l.eventType")
    List<Object[]> countByCreatorGroupedByEvent(@Param("creatorId") Long creatorId,
                                                @Param("events") Collection<PostNotificationEvent> events);

    /**
     * 작성자 조치 이력 (§54-3) — 알림 이력에서 센다.
     *
     * <p>{@code post_suspension}에서 세면 보관 만료로 게시물이 <b>파기될 때 행이 함께 사라져</b> 횟수가 줄어든다.
     * 알림 이력은 FK가 없고 영구 보존이다.
     *
     * <ul>
     *   <li>노출 중지 = SUSPENDED − SUSPENSION_CANCELED (운영자가 거둔 조치는 작성자 이력이 아니다)</li>
     *   <li>삭제 = APPEAL_REJECTED + DELETED_BY_EXPIRE (제재로 끝난 삭제만 · 본인 삭제는 다투지 않는 출구라 세지 않는다)</li>
     * </ul>
     */
    default PostSanctionCounts countSanctionEventsByCreator(Long creatorId) {
        if (creatorId == null) {
            return PostSanctionCounts.NONE;
        }
        Map<PostNotificationEvent, Long> counts = new EnumMap<>(PostNotificationEvent.class);
        for (Object[] row : countByCreatorGroupedByEvent(creatorId, List.of(
                PostNotificationEvent.SUSPENDED, PostNotificationEvent.SUSPENSION_CANCELED,
                PostNotificationEvent.APPEAL_REJECTED, PostNotificationEvent.DELETED_BY_EXPIRE))) {
            counts.put((PostNotificationEvent) row[0], ((Number) row[1]).longValue());
        }
        long suspended = counts.getOrDefault(PostNotificationEvent.SUSPENDED, 0L)
                - counts.getOrDefault(PostNotificationEvent.SUSPENSION_CANCELED, 0L);
        long deleted = counts.getOrDefault(PostNotificationEvent.APPEAL_REJECTED, 0L)
                + counts.getOrDefault(PostNotificationEvent.DELETED_BY_EXPIRE, 0L);
        return new PostSanctionCounts(Math.max(suspended, 0L), deleted);
    }
}
