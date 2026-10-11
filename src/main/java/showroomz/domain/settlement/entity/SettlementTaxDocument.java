package showroomz.domain.settlement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.domain.settlement.type.TaxInvoiceRejectReason;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 증빙 문서 1행(44 어드민 설계서 1-6 · 5절) — 원천징수영수증 · 인플루언서 세금계산서 · 브랜드 세금계산서 · 수정세금계산서.
 *
 * <p><b>상태 전이는 리포지토리 조건부 UPDATE 로만 바꾼다</b>({@code SettlementTaxDocumentRepository}) — 이 클래스에는 생성 외의
 * 쓰기 메서드가 없다. 금액 · 상대방은 생성 시점의 스냅샷이다. 반려 → 재제출은 같은 행이다(이전 반려 사유는 이력에 남는다).
 */
@Entity
@Table(name = "settlement_tax_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementTaxDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private TaxDocumentType type;

    /** 수정세금계산서의 차감 행(V182) — 그 외 null. */
    @Column(name = "clawback_id")
    private Long clawbackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TaxDocumentStatus status;

    @Column(name = "supply_amount", nullable = false)
    private long supplyAmount;

    @Column(name = "vat_amount", nullable = false)
    private long vatAmount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "counterparty_name", length = 100)
    private String counterpartyName;

    @Column(name = "counterparty_reg_number", length = 20)
    private String counterpartyRegNumber;

    @Column(name = "counterparty_email", length = 255)
    private String counterpartyEmail;

    @Column(name = "approval_number", length = 26)
    private String approvalNumber;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "file_key", length = 512)
    private String fileKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 32)
    private TaxInvoiceRejectReason rejectReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static SettlementTaxDocument create(Long settlementId, TaxDocumentType type, TaxDocumentStatus status,
                                               long supplyAmount, long vatAmount, String counterpartyName,
                                               String counterpartyRegNumber, String counterpartyEmail,
                                               LocalDate dueDate, LocalDateTime createdAt) {
        SettlementTaxDocument document = new SettlementTaxDocument();
        document.settlementId = settlementId;
        document.type = type;
        document.status = status;
        document.supplyAmount = supplyAmount;
        document.vatAmount = vatAmount;
        document.totalAmount = supplyAmount + vatAmount;
        document.counterpartyName = counterpartyName;
        document.counterpartyRegNumber = counterpartyRegNumber;
        document.counterpartyEmail = counterpartyEmail;
        document.dueDate = dueDate;
        document.createdAt = createdAt;
        return document;
    }

    /** 수정세금계산서 — 차감 행에 묶는다(차감 건마다 1행). */
    public SettlementTaxDocument withClawback(Long clawbackId) {
        this.clawbackId = clawbackId;
        return this;
    }

    public boolean hasFile() {
        return fileKey != null;
    }
}
