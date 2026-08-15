package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryDeleteReason;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 문의 (§23) — 소비자가 작성하고 브랜드(파트너센터)가 답변한다.
 *
 * <p>상태는 두 축으로 나뉜다 (§23-1). 답변 축은 {@link InquiryStatus},
 * 노출 축은 {@link InquiryExposureStatus}다. 화면에서는 한 열에 합쳐 보여주지만
 * 내부적으로는 별개 값이라, 삭제 요청이 반려되면 답변 축 값 그대로 복귀한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "PRODUCT_INQUIRY")
public class ProductInquiry extends BaseTimeEntity {

    /** 소비자 문의 본문 상한 (§23-3 · 어드민 확정값) */
    public static final int MAX_CONTENT_LENGTH = 250;

    /** 소비자 첨부 사진 최대 장수 (§23-3) */
    public static final int MAX_IMAGE_COUNT = 3;

    /** 브랜드 답변 상한 (§23-4) — [근거 대기] 잠정치 */
    public static final int MAX_ANSWER_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_INQUIRY_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private ProductInquiryType type;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 비밀글 여부 — 작성자가 지정하며 브랜드는 바꿀 수 없다 (§23-6 ③) */
    @Column(name = "IS_SECRET", nullable = false)
    private boolean secret;

    /** 첨부 사진 URL — 최대 3장 (§23-3) */
    @ElementCollection
    @CollectionTable(
            name = "PRODUCT_INQUIRY_IMAGES",
            joinColumns = @JoinColumn(name = "PRODUCT_INQUIRY_ID")
    )
    @Column(name = "IMAGE_URL", length = 512)
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "ANSWER_CONTENT", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "ANSWERED_AT")
    private LocalDateTime answeredAt;

    /** 답변 수정 시각 — 조용한 수정을 막기 위해 등록 시각과 병기한다 (§23-4) */
    @Column(name = "ANSWER_MODIFIED_AT")
    private LocalDateTime answerModifiedAt;

    /** 답변 축 (§23-1) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private InquiryStatus status;

    /** 노출 축 (§23-1) */
    @Enumerated(EnumType.STRING)
    @Column(name = "EXPOSURE_STATUS", nullable = false, length = 20)
    private InquiryExposureStatus exposureStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "DELETE_REQUEST_REASON", length = 32)
    private ProductInquiryDeleteReason deleteRequestReason;

    @Column(name = "DELETE_REQUEST_DETAIL", length = 500)
    private String deleteRequestDetail;

    @Column(name = "DELETE_REQUESTED_AT")
    private LocalDateTime deleteRequestedAt;

    /** 삭제 요청 처리 시각 — 반려·집행 공통 */
    @Column(name = "DELETE_REVIEWED_AT")
    private LocalDateTime deleteReviewedAt;

    /** 운영자 반려 사유 — 요청 브랜드에게 전달된다 (§23-5) */
    @Column(name = "DELETE_REJECT_REASON", length = 500)
    private String deleteRejectReason;

    /** 운영자 삭제 사유 — 내부 기록이라 브랜드·작성자에게 표시하지 않는다 (§23-5) */
    @Column(name = "DELETE_REASON", length = 500)
    private String deleteReason;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Builder
    public ProductInquiry(Users user, Product product, ProductInquiryType type, String content,
                          boolean secret, List<String> imageUrls) {
        this.user = user;
        this.product = product;
        this.type = type;
        this.content = content;
        this.secret = secret;
        if (imageUrls != null) {
            this.imageUrls = new ArrayList<>(imageUrls);
        }
        this.status = InquiryStatus.WAITING;
        this.exposureStatus = InquiryExposureStatus.NORMAL;
    }

    /** 브랜드 답변 등록 — 등록 즉시 공개 콘텐츠로 전환된다 (§23-4) */
    public void registerAnswer(String answerContent) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.status = InquiryStatus.ANSWERED;
    }

    /**
     * 브랜드 답변 수정 (§23-4) — 등록 시각은 유지하고 수정 시각만 갱신한다.
     * 잘못된 안내를 고칠 경로가 없으면 브랜드가 삭제 요청으로 우회하게 되므로 허용한다.
     */
    public void modifyAnswer(String answerContent) {
        this.answerContent = answerContent;
        this.answerModifiedAt = LocalDateTime.now();
    }

    /** 소비자의 문의 수정 — 비밀글 여부는 작성 시점 값을 그대로 둔다 */
    public void update(ProductInquiryType type, String content, List<String> imageUrls) {
        this.type = type;
        this.content = content;
        this.imageUrls = imageUrls != null ? new ArrayList<>(imageUrls) : new ArrayList<>();
    }

    /**
     * 브랜드의 삭제 요청 (§23-5) — 답변대기·답변완료 어느 쪽에서도 가능하다.
     * 답변 축은 건드리지 않는다. 검토 중에도 문의는 계속 게시된다.
     */
    public void requestDelete(ProductInquiryDeleteReason reason, String detail) {
        this.exposureStatus = InquiryExposureStatus.DELETE_REQUESTED;
        this.deleteRequestReason = reason;
        this.deleteRequestDetail = detail;
        this.deleteRequestedAt = LocalDateTime.now();
        this.deleteReviewedAt = null;
        this.deleteRejectReason = null;
    }

    /**
     * 운영자의 삭제 요청 반려 (§23-5) — 노출 축만 정상으로 돌리면
     * 답변 축은 보존돼 있으므로 요청 직전 상태로 그대로 복귀한다.
     */
    public void rejectDeleteRequest(String rejectReason) {
        this.exposureStatus = InquiryExposureStatus.NORMAL;
        this.deleteReviewedAt = LocalDateTime.now();
        this.deleteRejectReason = rejectReason;
    }

    /** 운영자의 삭제 집행 (§23-5) — 질문과 브랜드 답변이 함께 소비자 화면에서 내려간다. */
    public void executeDelete(String internalReason) {
        LocalDateTime now = LocalDateTime.now();
        this.exposureStatus = InquiryExposureStatus.DELETED;
        this.deleteReason = internalReason;
        this.deleteReviewedAt = now;
        this.deletedAt = now;
    }

    public boolean isAnswered() {
        return this.status == InquiryStatus.ANSWERED;
    }

    public boolean isDeleteRequested() {
        return this.exposureStatus == InquiryExposureStatus.DELETE_REQUESTED;
    }

    public boolean isDeleted() {
        return this.exposureStatus == InquiryExposureStatus.DELETED;
    }
}
