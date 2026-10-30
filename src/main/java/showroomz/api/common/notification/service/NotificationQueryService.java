package showroomz.api.common.notification.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.api.common.notification.dto.NotificationSummary;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알림함 조회 · 읽음(1017 설계서 1-7) — 세 수신자 API 가 같은 서비스를 쓴다. 보관 기간 안의 행만 보인다(소비자 90일 · 그 밖 30일).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    @PersistenceContext
    private EntityManager em;

    private final NotificationRepository notificationRepository;

    public PageResponse<NotificationItem> list(NotificationRecipientType type, Long recipientId, boolean unreadOnly,
                                               int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Notification> result = notificationRepository.findInbox(type, recipientId, since(type), unreadOnly,
                PageRequest.of(Math.max(page, 1) - 1, safeSize));
        Avatars avatars = avatars(result.getContent());
        Map<Long, Long> orderIds = orderIds(result.getContent());
        return new PageResponse<>(result.getContent().stream().map(n -> toItem(n, avatars, orderIds)).toList(), result);
    }

    public NotificationSummary summary(NotificationRecipientType type, Long recipientId) {
        return new NotificationSummary(notificationRepository.countUnread(type, recipientId, since(type)));
    }

    /** 소비자 종 배지 — 목록에 마지막으로 들어간 뒤 새로 온 수(C11 「목록에 들어오면 배지만 초기화」). */
    public long userBadgeCount(Long userId, LocalDateTime seenAt) {
        LocalDateTime after = seenAt == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : seenAt;
        return notificationRepository.countUserBadge(userId, after, since(NotificationRecipientType.USER));
    }

    @Transactional
    public void markRead(NotificationRecipientType type, Long recipientId, Long notificationId) {
        Notification notification = notificationRepository.findMine(notificationId, type, recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(LocalDateTime.now());
    }

    @Transactional
    public int markAllRead(NotificationRecipientType type, Long recipientId) {
        return notificationRepository.markAllRead(type, recipientId, LocalDateTime.now());
    }

    private static LocalDateTime since(NotificationRecipientType type) {
        return LocalDateTime.now().minusDays(type.getRetentionDays());
    }

    // ------------------------------------------------------------------ 조립

    private record Avatars(Map<Long, String> images, Set<Long> live) {
    }

    /** AVATAR 는 읽는 시점의 프로필 · 진행 중 공구(로즈 링)를 붙인다 — 지금 상태를 보여야 한다. */
    private Avatars avatars(List<Notification> rows) {
        Set<Long> creatorIds = rows.stream()
                .filter(n -> n.getImageKind() == NotificationImageKind.AVATAR && n.getImageRef() != null)
                .map(n -> parseLong(n.getImageRef())).filter(Objects::nonNull).collect(Collectors.toSet());
        if (creatorIds.isEmpty()) {
            return new Avatars(Map.of(), Set.of());
        }
        Map<Long, String> images = new HashMap<>();
        for (Object[] row : em.createQuery("""
                SELECT c.id, c.profileImageUrl FROM Creator c WHERE c.id IN :ids""", Object[].class)
                .setParameter("ids", creatorIds).getResultList()) {
            images.put((Long) row[0], (String) row[1]);
        }
        Set<Long> live = Set.copyOf(em.createQuery("""
                SELECT DISTINCT g.creator.id FROM GroupBuy g WHERE g.creator.id IN :ids
                  AND g.status IN (showroomz.domain.groupbuy.type.GroupBuyStatus.IN_PROGRESS,
                                   showroomz.domain.groupbuy.type.GroupBuyStatus.SUSPENSION_SCHEDULED)""", Long.class)
                .setParameter("ids", creatorIds).getResultList());
        return new Avatars(images, live);
    }

    /** ORDER_GROUP 대상의 하위주문 id → 주문 id(58 계획 6-4) — 페이지 단위 한 번. */
    private Map<Long, Long> orderIds(List<Notification> rows) {
        Set<Long> groupIds = rows.stream()
                .filter(n -> n.getTargetType() == NotificationTargetType.ORDER_GROUP && n.getTargetId() != null)
                .map(Notification::getTargetId).collect(Collectors.toSet());
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> orderIds = new HashMap<>();
        for (Object[] row : em.createQuery("""
                SELECT g.id, g.order.id FROM OrderDeliveryGroup g WHERE g.id IN :ids""", Object[].class)
                .setParameter("ids", groupIds).getResultList()) {
            orderIds.put((Long) row[0], (Long) row[1]);
        }
        return orderIds;
    }

    private static NotificationItem toItem(Notification n, Avatars avatars, Map<Long, Long> orderIds) {
        String imageUrl = n.getImageRef();
        boolean live = false;
        if (n.getImageKind() == NotificationImageKind.AVATAR) {
            Long creatorId = parseLong(n.getImageRef());
            imageUrl = creatorId == null ? null : avatars.images().get(creatorId);
            live = creatorId != null && avatars.live().contains(creatorId);
        }
        String actorLabel = n.getActorName() != null ? n.getActorName() : n.getActorType().getLabel();
        return new NotificationItem(n.getId(), n.getType(), n.getCategory(), n.getActorType(), actorLabel, n.getTitle(),
                n.getBody(), n.getEmphasis(),
                n.getTargetType() == null ? null
                        : new NotificationItem.Target(n.getTargetType(), n.getTargetId(), targetSub(n),
                                n.getTargetType() == NotificationTargetType.ORDER_GROUP && n.getTargetId() != null
                                        ? orderIds.get(n.getTargetId()) : null),
                n.getImageKind(), imageUrl, live, n.isAdvertising(), n.getTodoDueAt(), n.isRead(), n.getCreatedAt());
    }

    /** 기본정보 탭 키는 FE 탭 키(소문자)로 — 대문자로 저장된 이전 행도 여기서 맞춘다(58 계획 6-4). */
    private static String targetSub(Notification n) {
        String sub = n.getTargetSub();
        return n.getTargetType() == NotificationTargetType.BASIC_INFO_TAB && sub != null
                ? sub.toLowerCase(java.util.Locale.ROOT) : sub;
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
