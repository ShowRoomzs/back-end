package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.InquiryType;
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
    private InquiryType type;

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
    public OneToOneInquiry(Users user, InquiryType type, String content, List<String> imageUrls, Long orderId) {
        this.user = user;
        this.type = type;
        this.content = content;
        if (imageUrls != null) {
            this.imageUrls = imageUrls;
        }
        this.orderId = orderId;
        this.status = InquiryStatus.WAITING;
    }

    /** 운영자 답변 등록 — 1회만 가능하며 등록 즉시 답변완료로 전환된다 (§17-4) */
    public void registerAnswer(String answerContent, Long operatorId) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.answeredBy = operatorId;
        this.status = InquiryStatus.ANSWERED;
    }

    public void update(InquiryType type, String content, List<String> imageUrls, Long orderId) {
        this.type = type;
        this.content = content;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.orderId = orderId;
    }

    public boolean isAnswered() {
        return this.status == InquiryStatus.ANSWERED;
    }
}
