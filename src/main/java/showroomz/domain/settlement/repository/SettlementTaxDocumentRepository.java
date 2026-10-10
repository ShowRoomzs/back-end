package showroomz.domain.settlement.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.domain.settlement.type.TaxInvoiceRejectReason;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 증빙 문서 행(44 어드민 설계서 1-6 · 5절). 상태 전이는 <b>전부 조건부 UPDATE</b>다 — 0행이면 다른 요청이 먼저 바꿨다
 * (409 SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED).
 */
public interface SettlementTaxDocumentRepository extends JpaRepository<SettlementTaxDocument, Long> {

    List<SettlementTaxDocument> findBySettlementIdOrderByIdAsc(Long settlementId);

    List<SettlementTaxDocument> findBySettlementIdIn(Collection<Long> settlementIds);

    /** 정산의 유형별 1행 — 수정세금계산서(차감 건마다 1행) 밖의 유형. */
    Optional<SettlementTaxDocument> findBySettlementIdAndTypeAndClawbackIdIsNull(Long settlementId,
                                                                               TaxDocumentType type);

    /** 같은 승인번호가 다른 정산에서 이미 확인됐는가 — 중복 제출(5-2 · 400 SETTLEMENT_TAX_INVOICE_NUMBER_INVALID). */
    @Query("SELECT COUNT(d) > 0 FROM SettlementTaxDocument d WHERE d.approvalNumber = :approvalNumber "
            + "AND d.status = showroomz.domain.settlement.type.TaxDocumentStatus.VERIFIED AND d.settlementId <> :settlementId")
    boolean existsVerifiedApprovalNumberElsewhere(@Param("approvalNumber") String approvalNumber,
                                                  @Param("settlementId") Long settlementId);

    // ------------------------------------------------------------------ 전이(5-2 · 7-6 · 7-7 · 5-3)

    /** 인플루언서 입력(5-2) — 첫 입력 · 반려 뒤 재입력은 같은 행. 첨부는 호출자가 고른 값(새 첨부 · 기존 첨부)을 쓴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementTaxDocument d SET d.status = showroomz.domain.settlement.type.TaxDocumentStatus.SUBMITTED, "
            + "d.approvalNumber = :approvalNumber, d.fileKey = :fileKey, d.fileName = :fileName, "
            + "d.submittedAt = :submittedAt, d.submittedBy = :submittedBy, d.rejectReason = NULL, d.rejectedAt = NULL "
            + "WHERE d.id = :documentId AND d.status IN (showroomz.domain.settlement.type.TaxDocumentStatus.PENDING_INPUT, "
            + "    showroomz.domain.settlement.type.TaxDocumentStatus.REJECTED)")
    int submit(@Param("documentId") Long documentId, @Param("approvalNumber") String approvalNumber,
               @Param("fileKey") String fileKey, @Param("fileName") String fileName,
               @Param("submittedAt") LocalDateTime submittedAt, @Param("submittedBy") Long submittedBy);

    /** M4 확인(7-6). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementTaxDocument d SET d.status = showroomz.domain.settlement.type.TaxDocumentStatus.VERIFIED, "
            + "d.verifiedAt = :verifiedAt, d.verifiedBy = :verifiedBy "
            + "WHERE d.id = :documentId AND d.status = showroomz.domain.settlement.type.TaxDocumentStatus.SUBMITTED")
    int verify(@Param("documentId") Long documentId, @Param("verifiedAt") LocalDateTime verifiedAt,
               @Param("verifiedBy") Long verifiedBy);

    /** M4 반려(7-6) — 승인번호는 남긴다(스튜디오 D5b 가 반려된 번호를 보여 준다). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementTaxDocument d SET d.status = showroomz.domain.settlement.type.TaxDocumentStatus.REJECTED, "
            + "d.rejectReason = :reason, d.rejectedAt = :rejectedAt "
            + "WHERE d.id = :documentId AND d.status = showroomz.domain.settlement.type.TaxDocumentStatus.SUBMITTED")
    int reject(@Param("documentId") Long documentId, @Param("reason") TaxInvoiceRejectReason reason,
               @Param("rejectedAt") LocalDateTime rejectedAt);

    /** M5 브랜드 세금계산서 발행본 등록(7-7) — 정상 등록 뒤에는 다시 호출되지 않는다(정정은 수정세금계산서 새 행). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementTaxDocument d SET d.status = showroomz.domain.settlement.type.TaxDocumentStatus.ISSUED, "
            + "d.approvalNumber = :approvalNumber, d.issuedDate = :issuedDate, d.fileKey = :fileKey, "
            + "d.fileName = :fileName, d.issuedBy = :issuedBy "
            + "WHERE d.id = :documentId AND d.status = showroomz.domain.settlement.type.TaxDocumentStatus.PENDING_ISSUE")
    int issue(@Param("documentId") Long documentId, @Param("approvalNumber") String approvalNumber,
              @Param("issuedDate") LocalDate issuedDate, @Param("fileKey") String fileKey,
              @Param("fileName") String fileName, @Param("issuedBy") Long issuedBy);

    /** 원천징수영수증 생성 완료(5-3). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SettlementTaxDocument d SET d.status = showroomz.domain.settlement.type.TaxDocumentStatus.GENERATED, "
            + "d.fileKey = :fileKey, d.fileName = :fileName, d.generatedAt = :generatedAt "
            + "WHERE d.id = :documentId AND d.status = showroomz.domain.settlement.type.TaxDocumentStatus.PENDING_ISSUE")
    int markGenerated(@Param("documentId") Long documentId, @Param("fileKey") String fileKey,
                      @Param("fileName") String fileName, @Param("generatedAt") LocalDateTime generatedAt);

    // ------------------------------------------------------------------ 조회(07a 증빙 탭 · 배지 · 재시도)

    /** 재생성 대상(3-5) — 생성 대기 원천징수영수증. */
    @Query("SELECT d.id FROM SettlementTaxDocument d WHERE d.type = :type AND d.status = :status ORDER BY d.id ASC")
    List<Long> findIdsByTypeAndStatus(@Param("type") TaxDocumentType type, @Param("status") TaxDocumentStatus status,
                                      Pageable pageable);

    /** 07a 증빙 탭 — 처리 끝(확인 · 발행 · 생성 완료)을 뺀 문서. */
    @Query("SELECT d FROM SettlementTaxDocument d WHERE d.status NOT IN :done ORDER BY d.id ASC")
    List<SettlementTaxDocument> findOpen(@Param("done") Collection<TaxDocumentStatus> done);

    /** [유형, 상태, 건수] — 07a 툴바 · GNB 배지. */
    @Query("SELECT d.type, d.status, COUNT(d) FROM SettlementTaxDocument d GROUP BY d.type, d.status")
    List<Object[]> countByTypeAndStatus();

    /** 스튜디오 GNB 가산 — 인플루언서 세금계산서 입력 대기 · 반려. */
    @Query("SELECT COUNT(d) FROM SettlementTaxDocument d, Settlement s WHERE s.id = d.settlementId "
            + "AND s.creator.id = :creatorId "
            + "AND d.type = showroomz.domain.settlement.type.TaxDocumentType.CREATOR_TAX_INVOICE "
            + "AND d.status IN (showroomz.domain.settlement.type.TaxDocumentStatus.PENDING_INPUT, "
            + "    showroomz.domain.settlement.type.TaxDocumentStatus.REJECTED)")
    long countCreatorInvoicesToInput(@Param("creatorId") Long creatorId);
}
