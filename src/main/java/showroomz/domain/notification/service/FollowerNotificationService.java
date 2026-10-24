package showroomz.domain.notification.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.post.entity.Post;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 팔로우 쇼룸 알림(광고성 · 1017 설계서 1-3 · 1-6 · C11 · C15) — 새 게시물(하루 묶음) · 새 공구. 팔로워 수만 명인 쇼룸이 있으므로 500명씩
 * 키셋으로 끊어 읽고, 한 명당 알림함 행 하나 · 지연 푸시 하나를 쓴다. 수신 동의 · 탈퇴 · 정지는 쿼리에서 거른다(쓰기에서도 다시 본다).
 *
 * <p><b>새 게시물은 하루 묶음이다</b>(C15 「같은 쇼룸 소식은 하루에 한 번 묶어서」) — 같은 쇼룸 · 같은 발송 시각의 게시물은 대표 행 하나에
 * 합쳐 「새 게시물 3개」가 되고, 묶음 시각(기본 20시 · 야간이면 다음 아침)에 한 번 푸시된다. §24-3 의 「게시 즉시 팔로워 푸시」를 대체한다.
 */
@Service
@RequiredArgsConstructor
public class FollowerNotificationService {

    private static final int CHUNK_SIZE = 500;
    private static final DateTimeFormatter SLOT_KEY = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final int PREVIEW_LENGTH = 40;

    @PersistenceContext
    private EntityManager em;

    private final NotificationWriter writer;
    private final NotificationSchedule schedule;

    /**
     * 새 게시물 → 팔로워 묶음 알림. 호출자는 게시 커밋 뒤의 발송 스레드다.
     *
     * @return 대기열에 올린 팔로워 수(동의 · 중복 판정 전) — 0이면 받을 사람이 없다
     */
    @Transactional(readOnly = true)
    public int enqueuePostDigest(Creator creator, Post post, LocalDateTime now) {
        String showroom = creator.getShowroomName();
        String slot = schedule.digestSlot(now).format(SLOT_KEY);
        String preview = post == null ? null : preview(post.getContent());
        int count = 0;
        long afterId = 0L;
        while (true) {
            List<Long> userIds = followers(creator.getId(), "u.notificationSetting.followPostPushAgree", afterId);
            for (Long userId : userIds) {
                writer.writeSafely(NotificationCommand.toUser(userId, NotificationType.FOLLOW_POST_PUBLISHED)
                        .actorType(NotificationActorType.SHOWROOM).actorName(showroom)
                        .title(showroom + "에 새 게시물이 올라왔어요").body(preview).emphasis(showroom)
                        .imageKind(NotificationImageKind.AVATAR).imageRef(String.valueOf(creator.getId()))
                        .targetType(NotificationTargetType.SHOWROOM).targetId(creator.getId())
                        .dedupKey("FOLLOW_POST:" + userId + ":" + creator.getId() + ":" + slot)
                        .digest(true).digestTitleFormat(showroom + "에 새 게시물 %d개가 올라왔어요")
                        .build());
                count++;
            }
            if (userIds.size() < CHUNK_SIZE) {
                return count;
            }
            afterId = userIds.get(userIds.size() - 1);
        }
    }

    /** 공구 오픈 → 그 인플루언서 팔로워에게 「{쇼룸}에 새 공구가 열렸어요 · {상품} 외 N개」. @return 대기열 수 */
    @Transactional(readOnly = true)
    public int notifyGroupBuyOpened(Long groupBuyId) {
        List<Object[]> rows = em.createQuery("""
                SELECT g.creator.id, g.creator.showroomName FROM GroupBuy g WHERE g.id = :id""", Object[].class)
                .setParameter("id", groupBuyId).getResultList();
        if (rows.isEmpty()) {
            return 0;
        }
        Long creatorId = (Long) rows.get(0)[0];
        String showroom = (String) rows.get(0)[1];
        List<String> products = em.createQuery("""
                SELECT p.name FROM GroupBuy g JOIN g.contract ct, ContractItem ci JOIN ci.product p
                WHERE g.id = :id AND ci.contract = ct ORDER BY ci.sortOrder ASC""", String.class)
                .setParameter("id", groupBuyId).getResultList();
        String detail = products.isEmpty() ? "" : " · " + products.get(0)
                + (products.size() > 1 ? " 외 " + (products.size() - 1) + "개" : "");
        int count = 0;
        long afterId = 0L;
        while (true) {
            List<Long> userIds = followers(creatorId, "u.notificationSetting.followGroupBuyPushAgree", afterId);
            for (Long userId : userIds) {
                writer.writeSafely(NotificationCommand.toUser(userId, NotificationType.FOLLOW_GROUP_BUY_OPENED)
                        .actorType(NotificationActorType.SHOWROOM).actorName(showroom)
                        .title(showroom + "에 새 공구가 열렸어요" + detail).emphasis(showroom)
                        .imageKind(NotificationImageKind.AVATAR).imageRef(String.valueOf(creatorId))
                        .targetType(NotificationTargetType.GROUP_BUY).targetId(groupBuyId)
                        .dedupKey("GB_OPEN:" + userId + ":" + groupBuyId).build());
                count++;
            }
            if (userIds.size() < CHUNK_SIZE) {
                return count;
            }
            afterId = userIds.get(userIds.size() - 1);
        }
    }

    private List<Long> followers(Long creatorId, String consentPath, long afterId) {
        return em.createQuery("SELECT u.id FROM CreatorFollow cf JOIN cf.user u WHERE cf.creator.id = :creatorId "
                        + "AND u.status = showroomz.domain.member.user.type.UserStatus.NORMAL AND " + consentPath
                        + " = true AND u.id > :afterId ORDER BY u.id ASC", Long.class)
                .setParameter("creatorId", creatorId).setParameter("afterId", afterId)
                .setMaxResults(CHUNK_SIZE).getResultList();
    }

    private static String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String line = content.strip().replace('\n', ' ');
        return line.length() > PREVIEW_LENGTH ? line.substring(0, PREVIEW_LENGTH) + "…" : line;
    }
}
