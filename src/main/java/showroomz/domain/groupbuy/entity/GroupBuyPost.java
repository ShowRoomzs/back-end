package showroomz.domain.groupbuy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

/**
 * 공구 게시물 — 공구의 소비자 입구(공구 1:1, 필수).
 * 담기는 상품·공구가는 "게시물 → 공구 → 계약 상품 항목"으로 전체 자동 조회(따로 고르지 않음).
 * <ul>
 *   <li>본문은 글만(이미지·영상 없음) — 상품 이미지는 상품 상세페이지가 담당</li>
 *   <li>등록되어야 공구 준비완료(오픈) 승인 가능</li>
 *   <li>숨김은 운영자만 가능(인플루언서 임의 숨김 불가)</li>
 *   <li>예약·노출중 상태의 본문 수정 잠금 — 수정은 운영자 경유(오픈 승인 시 점검한 문구 보호)</li>
 *   <li>대가관계 표시는 시스템이 렌더링 시 자동 삽입(속성 관리 불필요, 제거 불가)</li>
 *   <li>공구 종료 시 자동 종료(CLOSED)</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "group_buy_post")
public class GroupBuyPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_post_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_buy_id", nullable = false, unique = true)
    private GroupBuy groupBuy;

    /** 게시된 쇼룸(인플루언서) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GroupBuyPostStatus status = GroupBuyPostStatus.DRAFT;

    /** 예약 노출 시각 — 공구 시작일 */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    public GroupBuyPost(GroupBuy groupBuy, Creator creator, String title, String content) {
        this.groupBuy = groupBuy;
        this.creator = creator;
        this.title = title;
        this.content = content;
        this.status = GroupBuyPostStatus.DRAFT;
    }

    /** 작성중 본문 수정 — 예약·노출중에는 잠금(운영자 경유, 서비스 검증) */
    public void updateContent(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /** 등록(발행) → 예약. 공구 진행중 진입 시 자동 노출 */
    public void register(LocalDateTime scheduledAt) {
        this.status = GroupBuyPostStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
    }

    /** 공구 진행중 진입(자동) → 노출중 */
    public void expose() {
        this.status = GroupBuyPostStatus.VISIBLE;
    }

    /** 운영자 수동 숨김(가역) */
    public void hide() {
        this.status = GroupBuyPostStatus.HIDDEN;
    }

    /** 운영자 재노출 */
    public void reExpose() {
        this.status = GroupBuyPostStatus.VISIBLE;
    }

    /** 공구 종료 → 자동 종료(노출 마감) */
    public void close() {
        this.status = GroupBuyPostStatus.CLOSED;
    }

    /** 예약·노출중은 본문 수정 잠금 */
    public boolean isContentLocked() {
        return status == GroupBuyPostStatus.SCHEDULED || status == GroupBuyPostStatus.VISIBLE;
    }

    public boolean isRegistered() {
        return status != GroupBuyPostStatus.DRAFT;
    }
}
