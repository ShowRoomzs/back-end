package showroomz.api.creator.basicinfo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.creator.basicinfo.dto.CreatorBasicInfoDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorWithdrawalChecklist;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;
import showroomz.domain.member.creator.type.CreatorWithdrawalStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.MaskingUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 스튜디오 기본정보 관리 — 계정 탭(§50-1 · §51-5 · 50 설계서 4절). 탈퇴 판정 · 신청 · 취소는 도메인 서비스
 * ({@link CreatorWithdrawalService} · {@link CreatorWithdrawalEligibility})를 그대로 부르고, 여기서는 화면 응답만 조립한다.
 */
@Service
@RequiredArgsConstructor
public class CreatorAccountTabService {

    private final CreatorRepository creatorRepository;
    private final CreatorWithdrawalRequestRepository withdrawalRequests;
    private final CreatorWithdrawalService withdrawalService;
    private final CreatorWithdrawalEligibility eligibility;
    private final CreatorWithdrawalChecklist checklist;

    @Transactional(readOnly = true)
    public CreatorBasicInfoDto.AccountResponse getAccount(Long userId) {
        return toResponse(creatorOf(userId));
    }

    /** 업무 이메일 — 즉시 저장 · 인증 메일 없음 · 중복 검사 없음(50 설계서 Q2 · Q3). 처리 이력은 남기지 않는다. */
    @Transactional
    public CreatorBasicInfoDto.AccountResponse updateBusinessEmail(Long userId,
                                                                   CreatorBasicInfoDto.UpdateBusinessEmailRequest request) {
        Creator creator = creatorOf(userId);
        requireNotWithdrawn(creator);
        if (!request.getBusinessEmail().equals(creator.getBusinessEmail())) {
            creator.changeBusinessEmail(request.getBusinessEmail());
        }
        return toResponse(creator);
    }

    @Transactional
    public CreatorBasicInfoDto.WithdrawalRequestResponse requestWithdrawal(Long userId, String reason) {
        Creator creator = creatorOf(userId);
        CreatorWithdrawalRequest saved = withdrawalService.request(creator.getId(), reason, LocalDateTime.now());
        return CreatorBasicInfoDto.WithdrawalRequestResponse.builder()
                .requestId(saved.getId())
                .requestedAt(saved.getRequestedAt())
                .dueDate(saved.getDueDate())
                .build();
    }

    @Transactional
    public void cancelWithdrawal(Long userId, Long requestId) {
        withdrawalService.cancel(creatorOf(userId).getId(), requestId, LocalDateTime.now());
    }

    @Transactional
    public void acknowledgeWithdrawalRejection(Long userId, Long requestId) {
        withdrawalService.acknowledgeRejection(creatorOf(userId).getId(), requestId, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ 조립

    private CreatorBasicInfoDto.AccountResponse toResponse(Creator creator) {
        ProviderType provider = creator.getUser() == null ? null : creator.getUser().getProviderType();
        return CreatorBasicInfoDto.AccountResponse.builder()
                .identity(CreatorBasicInfoDto.Identity.builder()
                        .realName(MaskingUtils.maskNameMiddle(creator.getRealName()))
                        .birthday(creator.getBirthday())
                        .phoneNumber(MaskingUtils.maskPhoneNumber(creator.getPhoneNumber()))
                        .build())
                .businessEmail(creator.getBusinessEmail())
                .login(CreatorBasicInfoDto.Login.builder()
                        .provider(provider == null ? null : provider.name())
                        .providerLabel(providerLabel(provider))
                        .build())
                .withdrawal(withdrawal(creator.getId()))
                .build();
    }

    /** 상태 판정 — ① 열린 신청 ② 최근 신청이 확인 안 한 반려 ③ 조건 판정식(50 설계서 4-1). */
    private CreatorBasicInfoDto.Withdrawal withdrawal(Long creatorId) {
        List<CreatorWithdrawalChecklist.Condition> conditions = checklist.read(creatorId);
        Optional<CreatorWithdrawalRequest> pending = withdrawalRequests
                .findFirstByCreatorIdAndStatusOrderByRequestedAtDesc(creatorId, CreatorWithdrawalStatus.PENDING);
        Optional<CreatorWithdrawalRequest> rejected = pending.isPresent() ? Optional.empty()
                : withdrawalRequests.findFirstByCreatorIdOrderByRequestedAtDescIdDesc(creatorId)
                        .filter(CreatorWithdrawalRequest::isUnacknowledgedRejection);

        String state;
        if (pending.isPresent()) {
            state = "PENDING";
        } else if (rejected.isPresent()) {
            state = "REJECTED";
        } else {
            state = eligibility.evaluate(creatorId).eligible() ? "AVAILABLE" : "BLOCKED";
        }

        return CreatorBasicInfoDto.Withdrawal.builder()
                .state(state)
                .satisfiedCount((int) conditions.stream().filter(CreatorWithdrawalChecklist.Condition::satisfied).count())
                .totalCount(conditions.size())
                .conditions(conditions.stream().map(CreatorAccountTabService::toCondition).toList())
                .approvalDueBusinessDays(CreatorWithdrawalService.APPROVAL_DUE_BUSINESS_DAYS)
                .pendingRequest(pending.map(r -> CreatorBasicInfoDto.PendingWithdrawal.builder()
                        .requestId(r.getId())
                        .requestedAt(r.getRequestedAt())
                        .reasonText(r.getReasonText())
                        .dueDate(r.getDueDate())
                        .build()).orElse(null))
                .rejection(rejected.map(r -> CreatorBasicInfoDto.WithdrawalRejection.builder()
                        .requestId(r.getId())
                        .rejectedAt(r.getProcessedAt())
                        .reasonCode(r.getRejectReason() == null ? null : r.getRejectReason().name())
                        .reasonLabel(r.getRejectReason() == null ? null : r.getRejectReason().getLabel())
                        .note(r.getRejectNote())
                        .build()).orElse(null))
                .build();
    }

    private static CreatorBasicInfoDto.WithdrawalCondition toCondition(CreatorWithdrawalChecklist.Condition c) {
        return CreatorBasicInfoDto.WithdrawalCondition.builder()
                .code(c.condition().name())
                .label(c.condition().getLabel())
                .count(c.count())
                .satisfied(c.satisfied())
                .resolution(c.condition().getResolution())
                .breakdown(c.breakdown().stream()
                        .map(b -> CreatorBasicInfoDto.ConditionBreakdown.builder()
                                .key(b.key()).label(b.label()).count(b.count()).build())
                        .toList())
                .build();
    }

    private static String providerLabel(ProviderType provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
            case GOOGLE -> "구글";
            case APPLE -> "애플";
            case FACEBOOK -> "페이스북";
            case LOCAL -> "이메일";
        };
    }

    private Creator creatorOf(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private static void requireNotWithdrawn(Creator creator) {
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
    }
}
