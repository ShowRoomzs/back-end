package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.InquiryActorType;
import showroomz.domain.inquiry.type.ProductInquiryHistoryType;

import java.time.LocalDateTime;

/**
 * 상품 문의 처리 이력 (§23-3) — 파트너센터 상세 우측에 최신순으로 쌓인다.
 * 삭제 요청 반려처럼 별도 상태값을 만들지 않는 이벤트도 여기에 기록해 브랜드가 인지한다 (§23-5).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "PRODUCT_INQUIRY_HISTORY")
public class ProductInquiryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_INQUIRY_HISTORY_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_INQUIRY_ID", nullable = false)
    private ProductInquiry inquiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "HISTORY_TYPE", nullable = false, length = 32)
    private ProductInquiryHistoryType historyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTOR_TYPE", nullable = false, length = 16)
    private InquiryActorType actorType;

    /** 이력 라벨 뒤에 붙는 부가 문구 — 삭제 요청 사유, 비밀글 표기 같은 값 */
    @Column(name = "DETAIL", length = 500)
    private String detail;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ProductInquiryHistory(ProductInquiry inquiry, ProductInquiryHistoryType historyType, String detail) {
        this.inquiry = inquiry;
        this.historyType = historyType;
        this.actorType = historyType.getActorType();
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
