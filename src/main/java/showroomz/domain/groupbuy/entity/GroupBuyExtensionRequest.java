package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.GroupBuyActorType;

import java.time.LocalDateTime;

/**
 * 기간 연장 요청 — 공구당 1회(§29-6). 1회 규칙은 {@code UNIQUE(group_buy_id)}가 막는다(설계서 1-5).
 *
 * <p>요청만으로 공구의 {@code end_at}은 바뀌지 않는다. 수락(스튜디오)이 조건부 UPDATE로 바꾼다.
 */
@Entity
@Table(name = "group_buy_extension_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyExtensionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "extension_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false, unique = true)
    private GroupBuy groupBuy;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "extension_days", nullable = false)
    private Integer extensionDays;

    @Column(name = "reason", length = 300)
    private String reason;

    /** 요청 시점 스냅샷 — B4c 「08.21 23:55 → 08.28 23:55」. */
    @Column(name = "before_end_at", nullable = false)
    private LocalDateTime beforeEndAt;

    @Column(name = "after_end_at", nullable = false)
    private LocalDateTime afterEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExtensionRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_actor_type", length = 16)
    private GroupBuyActorType responseActorType;

    @Column(name = "reject_reason_code", length = 64)
    private String rejectReasonCode;

    @Column(name = "reject_memo", length = 1000)
    private String rejectMemo;

    public static GroupBuyExtensionRequest request(GroupBuy groupBuy, Long sellerId, int extensionDays,
                                                   String reason, LocalDateTime now) {
        return GroupBuyExtensionRequest.builder()
                .groupBuy(groupBuy)
                .requestedBy(sellerId)
                .extensionDays(extensionDays)
                .reason(reason)
                .beforeEndAt(groupBuy.getEndAt())
                // 새 종료 = 현재 종료 + N일. 시각은 그대로다(C1 「시작일은 변경할 수 없습니다」).
                .afterEndAt(groupBuy.getEndAt().plusDays(extensionDays))
                .status(ExtensionRequestStatus.PENDING)
                .requestedAt(now)
                .build();
    }

    public boolean isPending() {
        return status == ExtensionRequestStatus.PENDING;
    }

    /** 무응답 = 변경 없이 종결(§29-6). 종료 전이가 함께 닫는다. */
    public void expire(LocalDateTime now) {
        this.status = ExtensionRequestStatus.EXPIRED;
        this.respondedAt = now;
        this.responseActorType = GroupBuyActorType.SYSTEM;
    }
}
