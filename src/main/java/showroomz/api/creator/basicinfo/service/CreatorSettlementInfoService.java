package showroomz.api.creator.basicinfo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.basicinfo.dto.CreatorBasicInfoDto;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.service.CreatorSettlementAccountRecorder;
import showroomz.domain.member.creator.type.CreatorAccountVerificationStatus;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.ResidentRegistrationNumber;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.CreatorAccountVerificationService;
import showroomz.domain.settlement.service.PayoutHoldTransitions;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.MaskingUtils;
import showroomz.global.utils.SettlementAccountMasker;

import java.time.LocalDateTime;

/**
 * 스튜디오 기본정보 관리 — 정산 정보 탭(§50-2 · §50-3 · 50 설계서 5절). 금액은 내리지 않는다 — 정산 관리가 단일 소스다.
 */
@Service
@RequiredArgsConstructor
public class CreatorSettlementInfoService {

    private final CreatorRepository creatorRepository;
    private final SettlementProperties settlementProperties;
    private final CreatorBusinessTypeChangeReader businessTypeChanges;
    private final BankRepository bankRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final CreatorSettlementAccountRecorder accountRecorder;
    private final PayoutHoldTransitions payoutHolds;
    private final CreatorAccountVerificationService accountVerification;

    @Transactional(readOnly = true)
    public CreatorBasicInfoDto.SettlementInfoResponse getSettlement(Long userId) {
        return toResponse(creatorOf(userId));
    }

    /** 계좌 변경 확인 모달(T6 · 50 설계서 6-1) — 「지급 예정 N건(금액)의 입금이 보류됩니다」. 확정 전 행은 세지 않는다. */
    @Transactional(readOnly = true)
    public CreatorBasicInfoDto.AccountChangeImpact getAccountChangeImpact(Long userId) {
        long pendingCount = 0;
        long pendingAmount = 0;
        long inFlight = 0;
        for (Object[] row : payoutRepository.aggregateCreatorPendingPayouts(creatorOf(userId).getId())) {
            PayoutStatus status = (PayoutStatus) row[0];
            long count = ((Number) row[1]).longValue();
            if (status == PayoutStatus.REQUESTED) {
                inFlight += count;
            } else {
                pendingCount += count;
                pendingAmount += ((Number) row[2]).longValue();
            }
        }
        return CreatorBasicInfoDto.AccountChangeImpact.builder()
                .pendingPayoutCount(pendingCount)
                .pendingPayoutAmount(pendingAmount)
                .inFlightPayoutCount(inFlight)
                .build();
    }

    /**
     * 정산 계좌 변경 + 실명 확인 시작(T2 · T3 · T6 · 50 설계서 6-2). 순서가 곧 규칙이다 — 이전 계좌를 읽어 이력을 남기고, 값을 바꿔 「확인 중」으로
     * 두고, 지시 전 예정 행을 보류한 뒤, 커밋 뒤에 PG 셀러 계좌 검증을 부른다. 진행 중 지급이 있어도 막지 않는다(허용 + 보류).
     */
    @Transactional
    public CreatorBasicInfoDto.SettlementInfoResponse changeAccount(Long userId,
                                                                    CreatorBasicInfoDto.ChangeSettlementAccountRequest request) {
        Long creatorId = creatorOf(userId).getId();
        Creator creator = creatorRepository.findForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        Bank bank = bankRepository.findById(request.getBankCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_NOT_FOUND));
        if (creator.hasAccount(bank.getName(), request.getAccountNumber())) {
            throw new BusinessException(ErrorCode.CREATOR_ACCOUNT_UNCHANGED);
        }
        LocalDateTime now = LocalDateTime.now();
        accountRecorder.recordChange(creator, bank.getName(), request.getAccountNumber(), CreatorAdminActorType.CREATOR,
                creatorId, now);
        creator.changeSettlementAccount(bank.getName(), request.getAccountNumber(), request.getBankbookImageUrl(),
                request.getBankbookFileName(), now);
        payoutHolds.hold(SettlementPayee.CREATOR, creatorId, PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED, now);
        AfterCommit.run(() -> accountVerification.verify(creatorId));
        return toResponse(creator);
    }

    CreatorBasicInfoDto.SettlementInfoResponse toResponse(Creator creator) {
        boolean business = creator.getBusinessType() == CreatorBusinessType.BUSINESS;
        CreatorBusinessTypeChangeReader.View change = businessTypeChanges.read(creator);
        return CreatorBasicInfoDto.SettlementInfoResponse.builder()
                .businessType(CreatorBasicInfoDto.BusinessTypeInfo.builder()
                        .value(creator.getBusinessType() == null ? null : creator.getBusinessType().name())
                        .label(creator.getBusinessType() == null ? null : creator.getBusinessType().getDescription())
                        .confirmedAt(change.confirmedAt())
                        .build())
                .residentNumber(business ? null : CreatorBasicInfoDto.ResidentNumberInfo.builder()
                        .masked(ResidentRegistrationNumber.display(creator.getResidentRegistrationNumberMasked()))
                        .registeredAt(creator.getResidentNumberRegisteredAt())
                        .build())
                .business(business ? CreatorBasicInfoDto.BusinessInfo.builder()
                        .registrationNumber(creator.getBusinessRegistrationNumber())
                        .license(change.license() != null ? change.license() : CreatorBasicInfoDto.DocumentFile.builder()
                                .url(creator.getBusinessLicenseImageUrl())
                                .build())
                        .build() : null)
                .taxInvoiceRecipient(business ? taxInvoiceRecipient() : null)
                .account(account(creator, business))
                .businessTypeChange(change.banner())
                .actions(CreatorBasicInfoDto.SettlementActions.builder()
                        .businessTypeChange(change.pending() ? "CANCEL"
                                : business ? "REQUEST_TO_INDIVIDUAL" : "REQUEST_TO_BUSINESS")
                        .accountChange(true)
                        .build())
                .build();
    }

    private CreatorBasicInfoDto.AccountInfo account(Creator creator, boolean business) {
        CreatorAccountVerificationStatus status = creator.getAccountVerificationStatus();
        return CreatorBasicInfoDto.AccountInfo.builder()
                .bankName(creator.getBankName())
                .accountNumber(SettlementAccountMasker.mask(creator.getAccountNumber()))
                .holderName(MaskingUtils.maskNameMiddle(creator.getRealName()))
                .holderRule(business ? "REAL_NAME_OR_COMPANY" : "REAL_NAME")
                .bankbook(CreatorBasicInfoDto.DocumentFile.builder()
                        .url(creator.getBankbookImageUrl())
                        .fileName(creator.getBankbookFileName())
                        .submittedAt(creator.getBankbookSubmittedAt())
                        .build())
                .verification(CreatorBasicInfoDto.AccountVerification.builder()
                        .status(status.name())
                        .label(status.getLabel())
                        .tone(status.getTone())
                        .verifiedAt(creator.getAccountVerifiedAt())
                        .failReason(status == CreatorAccountVerificationStatus.FAILED
                                ? PayoutBlockReason.fromCode(creator.getAccountVerificationCode())
                                        .map(PayoutBlockReason::getLabel).orElse("계좌 확인 실패")
                                : null)
                        .build())
                .build();
    }

    private CreatorBasicInfoDto.TaxInvoiceRecipient taxInvoiceRecipient() {
        SettlementProperties.Platform platform = settlementProperties.getPlatform();
        return CreatorBasicInfoDto.TaxInvoiceRecipient.builder()
                .businessName(platform.getBusinessName())
                .registrationNumber(platform.getRegistrationNumber())
                .representative(platform.getRepresentative())
                .address(platform.getAddress())
                .taxEmail(platform.getTaxEmail())
                .build();
    }

    Creator creatorOf(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
