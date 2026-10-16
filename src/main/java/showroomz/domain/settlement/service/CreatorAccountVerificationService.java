package showroomz.domain.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.service.CreatorMemberNotifier;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerResult;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerStatus;
import showroomz.domain.settlement.type.PayoutBlockReason;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 인플루언서 정산 계좌 실명 확인(50 설계서 1-2 · 6-3 · 6-4) — PG 셀러 등록 · 수정의 계좌 검증 결과를 계좌 확인 상태로 옮기고, 확인이 끝나면
 * 보류된 리워드를 다시 판정한다. 외부 호출은 트랜잭션 밖({@link SettlementPartnerSyncService}) · 상태 반영은 짧은 쓰기 트랜잭션이다.
 *
 * <table>
 *   <tr><th>PG 결과</th><th>계좌 확인 상태</th></tr>
 *   <tr><td>승인 · 대기(본인인증 · KYC — 계좌와 다른 축)</td><td>{@code VERIFIED}</td></tr>
 *   <tr><td>실패 + 계좌 사유</td><td>{@code FAILED} — 다른 계좌로 바꿔야 풀린다</td></tr>
 *   <tr><td>그 밖(PG 장애 · 수정 거절 · 프로필 미완성)</td><td>{@code VERIFYING} 유지 — 지급 배치 앞단이 다시 시도한다</td></tr>
 * </table>
 */
@Slf4j
@Service
public class CreatorAccountVerificationService {

    /** 계좌 문제로 본 실패 — 토스 어댑터의 분류 코드({@code BANK_REJECTED} 포함). */
    static final Set<String> ACCOUNT_FAIL_CODES = Set.of(PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name(),
            PayoutBlockReason.ACCOUNT_INVALID.name(), PayoutBlockReason.UNSUPPORTED_BANK.name(),
            PayoutBlockReason.ACCOUNT_MISSING.name(), "BANK_REJECTED");
    private static final int RETRY_BATCH = 100;

    private final SettlementPartnerSyncService partnerSync;
    private final CreatorRepository creatorRepository;
    private final CreatorAccountPayoutHook payoutHook;
    private final CreatorMemberNotifier notifier;
    private final TransactionTemplate writeTransaction;

    public CreatorAccountVerificationService(SettlementPartnerSyncService partnerSync, CreatorRepository creatorRepository,
                                             CreatorAccountPayoutHook payoutHook, CreatorMemberNotifier notifier,
                                             PlatformTransactionManager transactionManager) {
        this.partnerSync = partnerSync;
        this.creatorRepository = creatorRepository;
        this.payoutHook = payoutHook;
        this.notifier = notifier;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 확인 1회 — 계좌 변경 커밋 직후 · 재시도 배치가 부른다. 예외를 밖으로 내지 않는다. 반영 뒤 상태를 돌려준다. */
    public CreatorAccountVerificationStatus verify(Long creatorId) {
        try {
            SettlementPartnerSyncService.CreatorAccountCheck check = partnerSync.verifyCreatorAccount(creatorId);
            return writeTransaction.execute(tx -> apply(creatorId, check, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.error("인플루언서 계좌 확인 반영 실패 - creatorId: {}", creatorId, e);
            return null;
        }
    }

    /** 지급 배치 앞단 0단계(6-4) — 「확인 중」으로 남은 인플루언서를 다시 확인한다. 확인 완료 수를 돌려준다. */
    public int retryVerifying() {
        List<Long> ids = creatorRepository.findIdsByAccountVerificationStatus(CreatorAccountVerificationStatus.VERIFYING,
                PageRequest.of(0, RETRY_BATCH));
        int verified = 0;
        for (Long id : ids) {
            if (verify(id) == CreatorAccountVerificationStatus.VERIFIED) {
                verified++;
            }
        }
        return verified;
    }

    private CreatorAccountVerificationStatus apply(Long creatorId, SettlementPartnerSyncService.CreatorAccountCheck check,
                                                   LocalDateTime now) {
        Creator creator = creatorRepository.findForUpdate(creatorId).orElse(null);
        if (creator == null) {
            return null;
        }
        if (creator.getAccountVerificationStatus() != CreatorAccountVerificationStatus.VERIFYING) {
            return creator.getAccountVerificationStatus();
        }
        if (!creator.hasAccount(check.bankName(), check.accountNumber())) {
            // 확인하는 사이 계좌가 또 바뀌었다 — 뒤 호출이 판정한다(결과 역전 방지).
            log.info("인플루언서 계좌 확인 결과 버림(계좌 변경됨) - creatorId: {}", creatorId);
            return creator.getAccountVerificationStatus();
        }
        PartnerResult result = check.result();
        if (result.status() == PartnerStatus.APPROVED || result.status() == PartnerStatus.PENDING) {
            creator.markAccountVerified(now);
            payoutHook.release(creatorId, now.toLocalDate(), now);
        } else if (result.failCode() != null && ACCOUNT_FAIL_CODES.contains(result.failCode())) {
            creator.markAccountFailed(result.failCode());
            notifier.accountVerificationFailed(creatorId, PayoutBlockReason.fromCode(result.failCode())
                    .map(PayoutBlockReason::getLabel).orElse(result.failCode()));
        } else {
            log.warn("인플루언서 계좌 확인 보류(재시도 대상) - creatorId: {}, code: {}, reason: {}", creatorId,
                    result.failCode(), result.failReason());
        }
        return creator.getAccountVerificationStatus();
    }
}
