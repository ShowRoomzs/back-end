package showroomz.api.admin.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.settlement.type.AdminPayoutAccountSource;
import showroomz.api.admin.settlement.type.AdminTaxInvoiceVerifyResult;
import showroomz.api.common.settlement.service.SettlementPdfUploads;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.SettlementHistoryRecorder;
import showroomz.domain.settlement.service.SettlementPayoutService;
import showroomz.domain.settlement.service.SettlementPayoutTransitions;
import showroomz.domain.settlement.service.SettlementTaxDocumentService;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 어드민 07b 쓰기 — M3 재분배(44 어드민 설계서 7-5). 운영자 조치는 전부 조건부 UPDATE + 이력 {@code ADMIN}(0-11).
 *
 * <p>금액을 다시 계산하지 않는다 — 실패한 행을 같은 금액으로 다시 지시할 뿐이다. 운영자는 계좌를 입력하지 않는다(출처만 고른다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettlementCommandService {

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementPayoutTransitions payoutTransitions;
    private final SettlementPayoutService payoutService;
    private final SettlementHistoryRecorder historyRecorder;
    private final AdminOperatorResolver operators;
    private final PersonalDataCipher cipher;
    private final SettlementProperties properties;
    private final SettlementTaxDocumentService taxDocumentService;

    /**
     * M3 재분배 — FAILED 행을 오늘 예정으로 되돌리고(횟수 + 1 · 계좌 재스냅샷) 정산을 지급 예정으로 재파생한다. 지시는 커밋 뒤 바로
     * 보낸다(배치를 기다리지 않는다). 결과가 다시 실패면 정산은 분배 실패로 돌아간다.
     */
    @Transactional
    public void redistribute(Long settlementId, Long payoutId, AdminPayoutAccountSource source, Long operatorId) {
        operators.operatorName(operatorId);
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementPayout payout = payoutRepository.findById(payoutId)
                .filter(p -> p.getSettlementId().equals(settlementId))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (settlement.getStatus() != SettlementStatus.PAYOUT_FAILED || payout.getStatus() != PayoutStatus.FAILED) {
            throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
        }
        int retryLimit = properties.getPayoutRetryLimit();
        if (payout.getAttempt() >= retryLimit) {
            throw new BusinessException(ErrorCode.SETTLEMENT_PAYOUT_RETRY_EXCEEDED);
        }

        String bankName;
        String accountNumberEnc;
        String holder;
        if (source == AdminPayoutAccountSource.CURRENT_PROFILE) {
            SettlementPayoutTransitions.Account current = payoutTransitions.currentAccount(settlement, payout.getPayee());
            if (payout.getPayee() != SettlementPayee.PLATFORM && current.isMissing()) {
                throw new BusinessException(ErrorCode.SETTLEMENT_ACCOUNT_MISSING);
            }
            bankName = current.bankName();
            accountNumberEnc = cipher.encrypt(current.accountNumber());
            holder = current.holder();
        } else {
            if (payout.getPayee() != SettlementPayee.PLATFORM && !payout.hasAccountSnapshot()) {
                throw new BusinessException(ErrorCode.SETTLEMENT_ACCOUNT_MISSING);
            }
            bankName = payout.getBankName();
            accountNumberEnc = payout.getAccountNumberEnc();
            holder = payout.getAccountHolder();
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int attempt = payout.getAttempt() + 1;
        SettlementPayee payee = payout.getPayee();
        if (payoutRepository.reschedule(payoutId, today, bankName, accountNumberEnc, holder, retryLimit) != 1) {
            throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
        }
        payoutTransitions.derive(settlementId, now);
        historyRecorder.record(settlementId, SettlementEventType.PAYOUT_RETRIED, SettlementActorType.ADMIN, operatorId,
                "%s · %d회차 · %s · 확정 금액 그대로".formatted(payee.getLabel(), attempt,
                        source == AdminPayoutAccountSource.CURRENT_PROFILE ? "회원 정보 현재 계좌" : "지난 지시 계좌"),
                now);
        AfterCommit.run(() -> {
            try {
                payoutService.distribute(settlementId, today, LocalDateTime.now());
            } catch (Exception e) {
                // 지시는 지급 배치가 다시 집는다(예정일 = 오늘) — 운영자 응답을 실패로 만들지 않는다.
                log.error("재분배 즉시 지시 실패 - settlementId: {}, payoutId: {}", settlementId, payoutId, e);
            }
        });
    }

    /** M4 승인번호 대조(7-6) — 확인이면 인플루언서 몫 지급 보류가 풀린다(확인일 + N영업일). */
    public void verifyTaxInvoice(Long settlementId, Long documentId, AdminTaxInvoiceVerifyResult result,
                                 Long operatorId) {
        operators.operatorName(operatorId);
        taxDocumentService.verify(settlementId, documentId, result.rejectReason(), operatorId, LocalDateTime.now());
    }

    /** M5 브랜드 세금계산서 발행본 등록(7-7) — 정상 등록 뒤에는 버튼이 사라진다. */
    public void issueBrandInvoice(Long settlementId, Long documentId, MultipartFile file, String approvalNumber,
                                  LocalDate issuedDate, Long operatorId) {
        operators.operatorName(operatorId);
        SettlementTaxDocumentService.Attachment pdf = SettlementPdfUploads.read(file,
                properties.getTaxInvoiceAttachmentMaxBytes());
        taxDocumentService.issueBrandInvoice(settlementId, documentId, pdf, approvalNumber, issuedDate, operatorId,
                LocalDateTime.now());
    }
}
