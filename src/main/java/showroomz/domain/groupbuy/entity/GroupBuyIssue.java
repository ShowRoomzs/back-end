package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;

import java.time.LocalDateTime;

/**
 * 이슈 스레드 — 정산과 무관한 이견을 여는 창구(§29-10). 이슈는 공구 상태를 바꾸지 않고 정산도 보류하지 않는다.
 *
 * <p>열린 건 1개 제약은 운영 DB의 생성 컬럼 {@code open_group_buy_id} UNIQUE가 건다(엔티티 미매핑).
 * 종결 후 새 이견은 새 행이다.
 */
@Entity
@Table(name = "group_buy_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GroupBuyIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_buy_id", nullable = false)
    private GroupBuy groupBuy;

    @Enumerated(EnumType.STRING)
    @Column(name = "opener_type", nullable = false, length = 16)
    private GroupBuyActorType openerType;

    @Column(name = "opener_id", nullable = false)
    private Long openerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 32)
    private GroupBuyIssueType issueType;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "thread_id")
    private Long threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GroupBuyIssueStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public static GroupBuyIssue open(GroupBuy groupBuy, GroupBuyActorType openerType, Long openerId,
                                     GroupBuyIssueType issueType, String content, Long threadId, LocalDateTime now) {
        return GroupBuyIssue.builder()
                .groupBuy(groupBuy)
                .openerType(openerType)
                .openerId(openerId)
                .issueType(issueType)
                .content(content)
                .threadId(threadId)
                .status(GroupBuyIssueStatus.OPEN)
                .openedAt(now)
                .build();
    }

    public void close(LocalDateTime now) {
        this.status = GroupBuyIssueStatus.CLOSED;
        this.closedAt = now;
    }
}
