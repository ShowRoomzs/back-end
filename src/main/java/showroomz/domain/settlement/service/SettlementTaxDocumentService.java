package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.port.SettlementTaxDocumentStorage;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.domain.settlement.type.TaxInvoiceRejectReason;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.regex.Pattern;

/**
 * 증빙 — MVP 수동(44 어드민 설계서 5절). 확정이 세금계산서 행을 만들고(5-1), 인플루언서가 승인번호를 입력하고(5-2), 운영자가
 * 대조하고(M4 · 7-6) 브랜드 발행본을 등록한다(M5 · 7-7). 인플루언서 몫 지급 완료는 원천징수영수증 행을 만든다(5-3).
 *
 * <p>외부 발행 서비스가 붙으면 상태 전이만 자동화되고 행 구조는 같다. 모든 전이는 조건부 UPDATE + 이력이다.
 */
@Service
@RequiredArgsConstructor
public class SettlementTaxDocumentService implements SettlementTaxDocumentHook {

    /** 국세청 승인번호 — 숫자 24자리 + 하이픈 2. */
    private static final Pattern APPROVAL_NUMBER = Pattern.compile("^\\d{8}-\\d{8}-\\d{8}$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");

    private final SettlementTaxDocumentRepository documentRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementTaxDocumentStorage storage;
    private final WithholdingReceiptGenerator receiptGenerator;
    private final SettlementNotifier notifier;
    private final SettlementProperties properties;
    private final BusinessCalendar calendar;

    public record Attachment(String fileName, byte[] bytes) {
    }

    // ------------------------------------------------------------------ 훅(확정 · 지급 완료)

    /**
     * 확정(3-2 #3) — 브랜드 세금계산서 발행 대기(공급가 = 리워드 · 부가세 = 리워드 부가세 · 기한 = 확정일이 속한 달의 다음 달 N일) ·
     * 사업자면 인플루언서 세금계산서 입력 대기(공급가 = 차감 후 리워드 · 부가세 · 「발행할 내용」). 0원이면 행을 만들지 않는다.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onConfirmed(Settlement confirmed, LocalDateTime confirmedAt) {
        Long settlementId = confirmed.getId();
        // 확정은 수취자 행 UPDATE 로 영속성 컨텍스트를 비운 뒤 부른다 — 마켓 · 판매자 · 인플루언서를 다시 읽는다.
        Settlement settlement = settlementRepository.findDetailById(settlementId).orElse(confirmed);
        StringBuilder detail = new StringBuilder();
        if (settlement.getRewardAmount() > 0
                && documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlementId,
                TaxDocumentType.BRAND_TAX_INVOICE).isEmpty()) {
            Seller seller = settlement.getMarket().getSeller();
            LocalDate dueDate = brandInvoiceDueDate(confirmedAt.toLocalDate());
            documentRepository.save(SettlementTaxDocument.create(settlementId, TaxDocumentType.BRAND_TAX_INVOICE,
                    TaxDocumentStatus.PENDING_ISSUE, settlement.getRewardAmount(), settlement.getRewardVatAmount(),
                    seller == null || seller.getCompanyName() == null ? settlement.getMarket().getMarketName()
                            : seller.getCompanyName(),
                    seller == null ? null : seller.getBusinessRegistrationNumber(),
                    seller == null ? null : seller.getTaxEmail(), dueDate, confirmedAt));
            detail.append("브랜드 세금계산서 발행 대기 · 기한 ").append(DAY.format(dueDate));
        }
        if (settlement.isBusinessCreator() && settlement.getRewardAfterClawback() > 0
                && documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlementId,
                TaxDocumentType.CREATOR_TAX_INVOICE).isEmpty()) {
            Creator creator = settlement.getCreator();
            documentRepository.save(SettlementTaxDocument.create(settlementId, TaxDocumentType.CREATOR_TAX_INVOICE,
                    TaxDocumentStatus.PENDING_INPUT, settlement.getRewardAfterClawback(),
                    settlement.getCreatorVatAmount(), creator.getRealName() == null ? creator.getShowroomName()
                            : creator.getRealName(), creator.getBusinessRegistrationNumber(),
                    creator.getBusinessEmail(), null, confirmedAt));
            detail.append(detail.isEmpty() ? "" : " · ").append("인플루언서 세금계산서 입력 대기");
        }
        if (!detail.isEmpty()) {
            historyRecorder.recordBySystem(settlementId, SettlementEventType.TAX_INVOICE_REQUESTED, detail.toString(),
                    confirmedAt);
        }
    }

    /** 인플루언서 몫 지급 완료(5-3) — 비사업자면 원천징수영수증 생성 대기 행 + 커밋 뒤 생성(비동기 · 실패해도 지급은 끝났다). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCreatorPaid(Long settlementId, LocalDateTime paidAt) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.isBusinessCreator() || documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(
                settlementId, TaxDocumentType.WITHHOLDING_RECEIPT).isPresent()) {
            return;
        }
        SettlementTaxDocument receipt = documentRepository.save(SettlementTaxDocument.create(settlementId,
                TaxDocumentType.WITHHOLDING_RECEIPT, TaxDocumentStatus.PENDING_ISSUE,
                settlement.getRewardAfterClawback(), 0, null, null, null, null, paidAt));
        Long documentId = receipt.getId();
        AfterCommit.run(() -> receiptGenerator.generateAsync(documentId));
    }

    // ------------------------------------------------------------------ 5-2 인플루언서 입력

    @Transactional
    public SettlementTaxDocument submitCreatorInvoice(Long settlementId, Long creatorId, String approvalNumber,
                                                      Attachment attachment, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .filter(s -> creatorId.equals(s.getCreatorId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (!settlement.isBusinessCreator()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_INVOICE_NOT_REQUIRED);
        }
        if (settlement.getStatus() != SettlementStatus.PAYOUT_SCHEDULED
                && settlement.getStatus() != SettlementStatus.PAYOUT_FAILED) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_INVOICE_NOT_OPEN);
        }
        SettlementTaxDocument document = documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlementId,
                        TaxDocumentType.CREATOR_TAX_INVOICE)
                .filter(d -> TaxDocumentStatus.OPEN_FOR_INPUT.contains(d.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_TAX_INVOICE_NOT_OPEN));
        String number = requireApprovalNumber(approvalNumber);
        if (documentRepository.existsVerifiedApprovalNumberElsewhere(number, settlementId)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_INVOICE_NUMBER_INVALID,
                    "이미 다른 정산에서 확인된 승인번호입니다.");
        }
        String fileKey = document.getFileKey();
        String fileName = document.getFileName();
        if (attachment != null) {
            fileKey = storage.put(settlementId, document.getId(), attachment.bytes());
            fileName = attachment.fileName();
        }
        Long documentId = document.getId();
        if (documentRepository.submit(documentId, number, fileKey, fileName, now, creatorId) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED);
        }
        historyRecorder.record(settlementId, SettlementEventType.TAX_INVOICE_SUBMITTED, SettlementActorType.CREATOR,
                creatorId, "승인번호 입력 · 대조 대기", now);
        AfterCommit.run(() -> notifier.taxInvoiceSubmitted(settlementId));
        return documentRepository.findById(documentId).orElseThrow();
    }

    // ------------------------------------------------------------------ 7-6 M4 대조

    /**
     * M4 — 운영자는 승인번호를 입력하지 않고 결과만 고른다. 확인이면 인플루언서 행 {@code BLOCKED → SCHEDULED(확인일 + N영업일)},
     * 반려면 사유와 함께 스튜디오 재입력으로 돌아간다(같은 행).
     *
     * @param rejectReason null 이면 확인(MATCH)
     */
    @Transactional
    public void verify(Long settlementId, Long documentId, TaxInvoiceRejectReason rejectReason, Long operatorId,
                       LocalDateTime now) {
        settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementTaxDocument document = documentOf(settlementId, documentId);
        if (document.getType() != TaxDocumentType.CREATOR_TAX_INVOICE
                || document.getStatus() != TaxDocumentStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED);
        }
        if (rejectReason != null) {
            if (documentRepository.reject(documentId, rejectReason, now) != 1) {
                throw new BusinessException(ErrorCode.SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED);
            }
            historyRecorder.record(settlementId, SettlementEventType.TAX_INVOICE_REJECTED, SettlementActorType.ADMIN,
                    operatorId, "반려 · " + rejectReason.getLabel(), now);
            AfterCommit.run(() -> notifier.taxInvoiceRejected(settlementId));
            return;
        }
        if (documentRepository.verify(documentId, now, operatorId) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED);
        }
        LocalDate dueDate = calendar.addBusinessDays(now.toLocalDate(), properties.getPayoutBusinessDays());
        payoutRepository.findBySettlementIdAndPayee(settlementId, SettlementPayee.CREATOR)
                .map(SettlementPayout::getId)
                .ifPresent(payoutId -> payoutRepository.updateStatusWhere(payoutId, EnumSet.of(PayoutStatus.BLOCKED),
                        PayoutStatus.SCHEDULED, dueDate));
        historyRecorder.record(settlementId, SettlementEventType.TAX_INVOICE_VERIFIED, SettlementActorType.ADMIN,
                operatorId, "승인번호 확인 · 인플루언서 몫 지급 예정 " + DAY.format(dueDate), now);
        AfterCommit.run(() -> notifier.taxInvoiceVerified(settlementId));
    }

    // ------------------------------------------------------------------ 7-7 M5 발행본 등록

    @Transactional
    public void issueBrandInvoice(Long settlementId, Long documentId, Attachment file, String approvalNumber,
                                  LocalDate issuedDate, Long operatorId, LocalDateTime now) {
        settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementTaxDocument document = documentOf(settlementId, documentId);
        if (!document.getType().isBrandInvoice() || document.getStatus() != TaxDocumentStatus.PENDING_ISSUE) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED);
        }
        String number = requireApprovalNumber(approvalNumber);
        if (issuedDate == null || file == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "발행본 파일과 발행일이 필요합니다.");
        }
        String fileKey = storage.put(settlementId, documentId, file.bytes());
        if (documentRepository.issue(documentId, number, issuedDate, fileKey, file.fileName(), operatorId) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED);
        }
        historyRecorder.record(settlementId, SettlementEventType.BRAND_INVOICE_ISSUED, SettlementActorType.ADMIN,
                operatorId, "%s 발행본 등록 · 발행일 %s".formatted(document.getType().getLabel(), issuedDate), now);
    }

    // ------------------------------------------------------------------ 읽기

    /** 파일 스트림 — 행에 파일이 없으면 null. */
    @Transactional(readOnly = true)
    public byte[] readFile(SettlementTaxDocument document) {
        return document.hasFile() ? storage.read(document.getFileKey()) : null;
    }

    /** 브랜드 세금계산서 기한 — 공급일(확정일)이 속한 달의 다음 달 N일. */
    public LocalDate brandInvoiceDueDate(LocalDate suppliedOn) {
        LocalDate nextMonth = suppliedOn.withDayOfMonth(1).plusMonths(1);
        return nextMonth.withDayOfMonth(Math.min(properties.getBrandInvoiceDueDay(), nextMonth.lengthOfMonth()));
    }

    private SettlementTaxDocument documentOf(Long settlementId, Long documentId) {
        return documentRepository.findById(documentId)
                .filter(d -> d.getSettlementId().equals(settlementId))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    private static String requireApprovalNumber(String approvalNumber) {
        String number = approvalNumber == null ? "" : approvalNumber.trim();
        if (!APPROVAL_NUMBER.matcher(number).matches()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_INVOICE_NUMBER_INVALID);
        }
        return number;
    }
}
