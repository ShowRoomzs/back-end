package showroomz.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림함 한 줄(1017 설계서 1-1) — 소비자 · 브랜드 · 인플루언서가 같은 테이블을 쓴다. 문구는 <b>발생 시점에 굳힌다</b>(공구명 · 상품명이
 * 뒤에 바뀌어도 「그때 그 말」). 이동 대상은 종류 · id 만 — 지금 상태의 상세로 간다.
 */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_type, recipient_id, created_at"),
        @Index(name = "idx_notification_unread", columnList = "recipient_type, recipient_id, read_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private NotificationRecipientType recipientType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 60)
    private NotificationType type;

    @Column(name = "category", nullable = false, length = 24)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private NotificationActorType actorType;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", length = 500)
    private String body;

    /** C11 굵게 — 대상(상품명 · 쇼룸명 · 금액). 제목 안의 부분 문자열이다. */
    @Column(name = "emphasis", length = 200)
    private String emphasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 40)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_sub", length = 40)
    private String targetSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_kind", length = 16)
    private NotificationImageKind imageKind;

    @Column(name = "image_ref", length = 500)
    private String imageRef;

    @Column(name = "advertising", nullable = false)
    private boolean advertising;

    /** C11 할 일 알림 — 로즈 한 줄 + 기한(회수 송장 등록 · 재발송비 결제). */
    @Column(name = "todo_due_at")
    private LocalDateTime todoDueAt;

    /** 같은 사건의 중복 방지 — 마감 임박(user · 공구) · 구매확정 D-1(하위주문) · 묶음(user · 쇼룸 · 날짜). */
    @Column(name = "dedup_key", length = 120, unique = true)
    private String dedupKey;

    /** 하루 묶음 대표 행에 쌓인 건수(「새 게시물 3개」). 묶음이 아니면 1. */
    @Column(name = "digest_count", nullable = false)
    private int digestCount;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(NotificationRecipientType recipientType, Long recipientId, NotificationType type,
                         NotificationActorType actorType, String actorName, String title, String body, String emphasis,
                         NotificationTargetType targetType, Long targetId, String targetSub,
                         NotificationImageKind imageKind, String imageRef, LocalDateTime todoDueAt, String dedupKey,
                         LocalDateTime createdAt) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.type = type;
        this.category = type.getCategory();
        this.actorType = actorType == null ? NotificationActorType.SYSTEM : actorType;
        this.actorName = actorName;
        this.title = truncate(title, 200);
        this.body = truncate(body, 500);
        this.emphasis = truncate(emphasis, 200);
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetSub = targetSub;
        this.imageKind = imageKind;
        this.imageRef = imageRef;
        this.advertising = type.isAdvertising();
        this.todoDueAt = todoDueAt;
        this.dedupKey = dedupKey;
        this.digestCount = 1;
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    /** 하루 묶음에 한 건 더 — 문구를 갈아 끼우고 다시 안 읽음 · 맨 위로. */
    public void absorbDigest(String title, String emphasis, LocalDateTime now) {
        this.digestCount++;
        this.title = truncate(title, 200);
        this.emphasis = truncate(emphasis, 200);
        this.readAt = null;
        this.createdAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
