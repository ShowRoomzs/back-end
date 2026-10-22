package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 문의 (§17) — 답변은 어드민(운영자)만 등록하며 마켓으로는 전달되지 않는다.
 * 답변은 1회로 종료되고 수정·삭제할 수 없다(§17-4).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ONE_TO_ONE_INQUIRY")
public class OneToOneInquiry extends BaseTimeEntity {

    /** 첨부 사진 최대 장수 (§17-5) */
    public static final int MAX_IMAGE_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INQUIRY_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    // 문의 유형 — FAQ 카테고리와 동일한 5종 (§17-2-1)
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private CsCategory type;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 이미지 URL 목록 (최대 5장)
    @ElementCollection
    @CollectionTable(
            name = "ONE_TO_ONE_INQUIRY_IMAGES",
            joinColumns = @JoinColumn(name = "INQUIRY_ID")
    )
    @Column(name = "IMAGE_URL", length = 512)
    private List<String> imageUrls = new ArrayList<>();

    /** 참조 주문 — 선택값. 주문 없이도 문의할 수 있다 (§17-3) */
    @Column(name = "ORDER_ID")
    private Long orderId;

    /**
     * 반려 이의 대상 클레임 — 앱 반려 안내(C10-5)의 「이의 제기」로 쓴 문의만 값이 있다(V176 · 기획 §38-8 B-12 자동 연결).
     * 작성 때 정해지고 수정으로 바뀌지 않는다. 어드민 반품·교환(06b)이 이 값으로 「소비자 이의」를 보인다.
     */
    @Column(name = "CLAIM_ID")
    private Long claimId;

    /**
     * 계정 이용 정지 소명 대상 정지 행 — 앱 정지 안내 시트의 「1:1 문의로 이의 신청」으로 쓴 문의만 값이 있다(V204 · 51-6 설계서 0-8).
     * 유형은 {@link CsCategory#ACCOUNT}로 고정되고 관련 주문이 없다. 「계정 이용 정지」는 FAQ 와 공유하는 5종을 깨지 않도록 유형 값이 아니라
     * 이 표식으로 구분한다. 작성 때 정해지고 수정으로 바뀌지 않는다.
     */
    @Column(name = "SANCTION_ID")
    private Long sanctionId;

    /**
     * 운임 환급 계좌 안내 대상 환불 큐 행 — 앱 C10-5 「귀책 변경 · 운임 환급」의 [1:1 문의]로 쓴 문의만 값이 있다(V210 · 1021 설계서 4-5).
     * 원래 결제액을 넘는 환급을 계좌로 보내야 해 계좌를 이 문의로 받는다. 어드민 06c M3 이 「1:1 문의 #N 로 받은 계좌」로 찾는다.
     */
    @Column(name = "REFUND_TASK_ID")
    private Long refundTaskId;

    // 답변 관련 필드
    @Column(name = "ANSWER_CONTENT", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "ANSWERED_AT")
    private LocalDateTime answeredAt;

    /** 답변을 등록한 운영자(Seller) ID — 답변완료 상태에서만 값이 있다 (§17-3 처리자) */
    @Column(name = "ANSWERED_BY")
    private Long answeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private InquiryStatus status;

    @Builder
    public OneToOneInquiry(Users user, CsCategory type, String content, List<String> imageUrls, Long orderId,
                           Long claimId, Long sanctionId, Long refundTaskId) {
        this.user = user;
        this.type = type;
        this.content = content;
        if (imageUrls != null) {
            this.imageUrls = imageUrls;
        }
        this.orderId = orderId;
        this.claimId = claimId;
        this.sanctionId = sanctionId;
        this.refundTaskId = refundTaskId;
        this.status = InquiryStatus.WAITING;
    }

    /** 화면에 보이는 유형명 — 「계정 이용 정지」 소명 문의(C20 1d)는 유형 값이 아니라 표식으로 구분한다. */
    public static final String SANCTION_APPEAL_TYPE_NAME = "계정 이용 정지";

    public boolean isSanctionAppeal() {
        return sanctionId != null;
    }

    /** 운임 환급 계좌 안내 문의의 유형명(1021 설계서 4-5) — 「계정 이용 정지」와 같은 방식으로 유형 값이 아니라 표식으로 구분한다. */
    public static final String REFUND_ACCOUNT_TYPE_NAME = "환불 계좌 안내";

    public boolean isRefundAccountNotice() {
        return refundTaskId != null;
    }

    public String typeName() {
        if (isSanctionAppeal()) {
            return SANCTION_APPEAL_TYPE_NAME;
        }
        return isRefundAccountNotice() ? REFUND_ACCOUNT_TYPE_NAME : type.getDescription();
    }

    /** 운영자 답변 등록 — 1회만 가능하며 등록 즉시 답변완료로 전환된다 (§17-4) */
    public void registerAnswer(String answerContent, Long operatorId) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.answeredBy = operatorId;
        this.status = InquiryStatus.ANSWERED;
    }

    public void update(CsCategory type, String content, List<String> imageUrls, Long orderId) {
        this.type = type;
        this.content = content;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.orderId = orderId;
    }

    public boolean isAnswered() {
        return this.status == InquiryStatus.ANSWERED;
    }
}
