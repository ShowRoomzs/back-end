package showroomz.domain.contract.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 계약 — 연결된 브랜드·인플루언서가 공구 조건(상품·공구가·리워드율·기간)을 확정하는 전자계약.
 * 외부 전자계약 솔루션(A안) 연동: 문서ID·체결 PDF·감사추적인증서는 참조만 보관.
 * <ul>
 *   <li>'계약 시작'은 연결됨 상태에서만 가능(서비스 검증)</li>
 *   <li>서명 순서: 인플루언서 → 브랜드. 부분서명은 플래그로 추적</li>
 *   <li>체결완료가 공구 생성 게이트(계약 1:1 공구)</li>
 *   <li>발송 취소(회수)는 인플루언서 서명 전에만 가능 → 작성중 복귀·수정·재발송</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "contract")
public class Contract extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private Connection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "group_buy_start_date")
    private LocalDate groupBuyStartDate;

    @Column(name = "group_buy_end_date")
    private LocalDate groupBuyEndDate;

    /** 외부 전자계약 솔루션 문서 ID */
    @Column(name = "external_document_id", length = 255)
    private String externalDocumentId;

    /** 체결 PDF 참조(다운로드 URL 등) */
    @Column(name = "signed_pdf_url", length = 2048)
    private String signedPdfUrl;

    /** 감사추적인증서 참조 */
    @Column(name = "audit_trail_cert_url", length = 2048)
    private String auditTrailCertUrl;

    @Column(name = "influencer_signed", nullable = false)
    private boolean influencerSigned = false;

    @Column(name = "brand_signed", nullable = false)
    private boolean brandSigned = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractProduct> contractProducts = new ArrayList<>();

    public Contract(Connection connection) {
        this.connection = connection;
        this.status = ContractStatus.DRAFT;
    }

    /** 작성중 — 협의항목 입력·저장 */
    public void updateTerms(LocalDate startDate, LocalDate endDate) {
        this.groupBuyStartDate = startDate;
        this.groupBuyEndDate = endDate;
    }

    public void addProduct(ContractProduct contractProduct) {
        this.contractProducts.add(contractProduct);
    }

    /** 계약서 발송 → 서명대기 (템플릿 서명요청 API) */
    public void send(String externalDocumentId) {
        this.externalDocumentId = externalDocumentId;
        this.status = ContractStatus.AWAITING_SIGN;
    }

    /** 발송 취소(회수) — 인플루언서 서명 전에만 → 작성중 복귀 (알림 #16) */
    public void recall() {
        if (influencerSigned) {
            throw new IllegalStateException("인플루언서 서명 후에는 회수할 수 없습니다.");
        }
        this.status = ContractStatus.DRAFT;
        this.externalDocumentId = null;
    }

    /** 인플루언서 서명(1차) */
    public void signByInfluencer() {
        this.influencerSigned = true;
    }

    /** 브랜드 최종 서명 → 전원 서명 완료 시 체결완료 (Webhook: all_signed) */
    public void signByBrand() {
        this.brandSigned = true;
    }

    /** 체결완료 — 공구 생성 게이트. 증거 참조 저장 */
    public void complete(String signedPdfUrl, String auditTrailCertUrl) {
        this.status = ContractStatus.COMPLETED;
        this.signedPdfUrl = signedPdfUrl;
        this.auditTrailCertUrl = auditTrailCertUrl;
        this.completedAt = LocalDateTime.now();
    }

    /** 일방 서명 거절 (Webhook: rejected) */
    public void reject() {
        this.status = ContractStatus.REJECTED;
    }

    /** 서명 기한 초과 */
    public void expire() {
        this.status = ContractStatus.EXPIRED;
    }

    /** 발송 후 철회 — 브랜드 또는 운영자 */
    public void cancel() {
        this.status = ContractStatus.CANCELED;
    }

    public boolean isCompleted() {
        return status == ContractStatus.COMPLETED;
    }
}
