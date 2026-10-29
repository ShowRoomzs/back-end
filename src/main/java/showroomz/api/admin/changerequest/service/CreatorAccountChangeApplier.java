package showroomz.api.admin.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.member.common.service.MemberNotifier;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.service.CreatorSettlementAccountRecorder;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.CreatorAccountVerificationService;
import showroomz.domain.settlement.service.PayoutHoldTransitions;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.PersonalDataCipher;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;

/**
 * 승인된 인플루언서 정산 계좌 변경을 반영한다(1021 결정 노트 기-22 · 설계서 8-2). 운영자 확인(통장 사본 · 예금주 = 실명)은 사람 검수이고,
 * 반영 뒤에도 PG 계좌 실존 확인은 그대로 돈다 — 통과하면 보류가 풀리고, 실패하면 「확인 실패」로 남아 지급을 막는다.
 *
 * <p>요청자 전략({@link ChangeRequestApplier})은 인플루언서 하나라 {@link CreatorBusinessTypeChangeApplier}가 유형을 보고 여기로 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class CreatorAccountChangeApplier {

    private final CreatorRepository creatorRepository;
    private final BankRepository bankRepository;
    private final CreatorSettlementAccountRecorder accountRecorder;
    private final CreatorAccountVerificationService accountVerification;
    private final PayoutHoldTransitions payoutHolds;
    private final MemberNotifier notifier;
    private final PersonalDataCipher cipher;

    /** 요청 행의 암호문을 읽으므로 {@code request.approve} 보다 먼저 부른다. */
    public void apply(ChangeRequest request, LocalDateTime now) {
        Creator creator = creatorRepository.findForUpdate(request.getCreator().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        Bank bank = bankRepository.findById(request.getAccountBankCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_NOT_FOUND));
        String accountNumber = cipher.decrypt(request.getAccountNumberEnc());
        // 이력의 주체는 요청한 인플루언서 본인이다(운영자는 확인만 한다).
        accountRecorder.recordChange(creator, bank.getName(), accountNumber, CreatorAdminActorType.CREATOR,
                creator.getId(), now);
        String fileName = request.getEvidenceFileName() == null || request.getEvidenceFileName().isBlank()
                ? null : request.getEvidenceFileName();
        creator.changeSettlementAccount(bank.getName(), accountNumber, request.getEvidenceFileUrl(), fileName, now);
        Long creatorId = creator.getId();
        // 보류는 요청 때 걸렸다 — PG 계좌 실존 확인이 통과하면 그 경로가 푼다.
        AfterCommit.run(() -> accountVerification.verify(creatorId));
    }

    public void notifyApproved(ChangeRequest request) {
        Long creatorId = request.getCreator().getId();
        String code = request.getRequestCode();
        AfterCommit.run(() -> notifier.accountChangeApproved(creatorId, code));
    }

    /** 반려 — 기존 계좌 그대로 · 요청 때 건 보류를 푼다(기존 계좌가 확인 실패 상태면 그 보류는 남는다). */
    public void onRejected(ChangeRequest request, ChangeRequestRejectReason reason, LocalDateTime now) {
        Creator creator = request.getCreator();
        if (creator.getAccountVerificationStatus() == null || !creator.getAccountVerificationStatus().blocksPayout()) {
            payoutHolds.release(SettlementPayee.CREATOR, creator.getId(), PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED,
                    PayoutHoldTransitions.DueRule.ORIGINAL_OR_TODAY, KstDates.today(now), now);
        }
        Long creatorId = creator.getId();
        String code = request.getRequestCode();
        String label = reason.descriptionFor(request.getType());
        AfterCommit.run(() -> notifier.accountChangeRejected(creatorId, code, label));
    }
}
