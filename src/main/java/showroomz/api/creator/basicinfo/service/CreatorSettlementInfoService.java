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
    private final showroomz.domain.changerequest.repository.ChangeRequestRepository changeRequestRepository;
    private final showroomz.domain.changerequest.service.ChangeRequestCodeGenerator codeGenerator;
    private final showroomz.global.utils.PersonalDataCipher cipher;

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
     * 정산 계좌 변경 → <b>변경 요청</b>(1021 결정 노트 기-22 · 설계서 8-2). 즉시 바꾸지 않고 운영자가 통장 사본 · 예금주(= 본인 실명)를 확인한 뒤
     * 반영한다. 확인 중에는 기존 계좌를 유지하고, 지급 요청이 나가기 전의 예정 지급은 보류한다(이미 PG 에 나간 건은 기존 계좌 그대로).
     * 열린 요청이 있으면(사업자 구분 변경 포함 · 인플루언서당 1건) 409.
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
        if (changeRequestRepository.findOpenForCreator(creatorId).isPresent()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }
        LocalDateTime now = LocalDateTime.now();
        // 파일명은 선택 — 요청 행 열이 NOT NULL 이라 빈 값으로 두고 반영 때 비운다.
        String fileName = request.getBankbookFileName() == null ? "" : request.getBankbookFileName().strip();
        showroomz.domain.changerequest.entity.ChangeRequest changeRequest =
                showroomz.domain.changerequest.entity.ChangeRequest.createForCreator(codeGenerator.next(), creator,
                        showroomz.domain.changerequest.type.ChangeRequestType.CREATOR_ACCOUNT,
                        request.getBankbookImageUrl(), fileName, 0L);
        changeRequest.addItem(showroomz.domain.changerequest.type.ChangeRequestField.CREATOR_ACCOUNT_BANK,
                creator.getBankName(), bank.getName(), 0);
        changeRequest.addItem(showroomz.domain.changerequest.type.ChangeRequestField.CREATOR_ACCOUNT_NUMBER,
                SettlementAccountMasker.mask(creator.getAccountNumber()),
                SettlementAccountMasker.mask(request.getAccountNumber()), 1);
        changeRequest.attachAccount(bank.getCode(), cipher.encrypt(request.getAccountNumber()), creator.getRealName());
        try {
            changeRequestRepository.saveAndFlush(changeRequest);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }
        // 확인 끝날 때까지 지급 요청 전 예정 지급은 보류 — PG 에 이미 나간 건(REQUESTED)은 기존 계좌 그대로(8-0 ②).
        payoutHolds.hold(SettlementPayee.CREATOR, creatorId, PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED, now);
        return toResponse(creator);
    }

    /** 확인 중인 계좌 변경 요청 취소(1021 기-22) — 기존 계좌 그대로 · 보류한 지급은 원래 예정일(지났으면 오늘)로 되돌린다. */
    @Transactional
    public CreatorBasicInfoDto.SettlementInfoResponse cancelAccountChange(Long userId) {
        Creator creator = creatorOf(userId);
        showroomz.domain.changerequest.entity.ChangeRequest open = changeRequestRepository.findOpenForCreator(creator.getId())
                .filter(r -> r.getType() == showroomz.domain.changerequest.type.ChangeRequestType.CREATOR_ACCOUNT)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING));
        open.cancel();
        LocalDateTime now = LocalDateTime.now();
        if (creator.getAccountVerificationStatus() == null || !creator.getAccountVerificationStatus().blocksPayout()) {
            payoutHolds.release(SettlementPayee.CREATOR, creator.getId(), PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED,
                    PayoutHoldTransitions.DueRule.ORIGINAL_OR_TODAY, now.toLocalDate(), now);
        }
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
                .pendingAccountChange(pendingAccountChange(creator))
                .build();
    }

    private CreatorBasicInfoDto.PendingAccountChange pendingAccountChange(Creator creator) {
        return changeRequestRepository.findOpenForCreator(creator.getId())
                .filter(r -> r.getType() == showroomz.domain.changerequest.type.ChangeRequestType.CREATOR_ACCOUNT)
                .map(r -> {
                    long count = 0;
                    long amount = 0;
                    for (Object[] row : payoutRepository.aggregateCreatorPendingPayouts(creator.getId())) {
                        if (row[0] != PayoutStatus.REQUESTED) {
                            count += ((Number) row[1]).longValue();
                            amount += ((Number) row[2]).longValue();
                        }
                    }
                    String bankName = r.getItems().stream()
                            .filter(i -> i.getFieldKey() == showroomz.domain.changerequest.type.ChangeRequestField.CREATOR_ACCOUNT_BANK)
                            .map(showroomz.domain.changerequest.entity.ChangeRequestItem::getRequestedValue).findFirst().orElse(null);
                    String masked = r.getItems().stream()
                            .filter(i -> i.getFieldKey() == showroomz.domain.changerequest.type.ChangeRequestField.CREATOR_ACCOUNT_NUMBER)
                            .map(showroomz.domain.changerequest.entity.ChangeRequestItem::getRequestedValue).findFirst().orElse(null);
                    return CreatorBasicInfoDto.PendingAccountChange.builder()
                            .requestId(r.getId())
                            .requestCode(r.getRequestCode())
                            .bankName(bankName)
                            .maskedAccountNumber(masked)
                            .holderName(MaskingUtils.maskNameMiddle(r.getAccountHolderName()))
                            .bankbookFileName(r.getEvidenceFileName())
                            .requestedAt(r.getRequestedAt())
                            .reviewBusinessDays(null)
                            .holdingPayoutCount(count)
                            .holdingPayoutAmount(amount)
                            .build();
                })
                .orElse(null);
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
