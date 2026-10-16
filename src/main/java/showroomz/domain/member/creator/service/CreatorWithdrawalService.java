package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorWithdrawalStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDateTime;

/**
 * 인플루언서 탈퇴 신청 · 취소의 <b>도메인 서비스</b>(§51-5 · 53 설계서 0-3). 스튜디오 화면 API(S1 · S4 ~ S8)는 범위 밖이고 그쪽이 이
 * 서비스를 부른다 — 조건 판정 · 열린 신청 1건 · 이력 기록이 어드민 심사와 같은 규칙에 묶이게 여기 둔다.
 */
@Service
@RequiredArgsConstructor
public class CreatorWithdrawalService {

    /** 승인 기한 안내 — 스튜디오 「영업일 5일 이내」(§51-5 · 무응답 자동 승인은 없다 · §55 B-7). */
    public static final int APPROVAL_DUE_BUSINESS_DAYS = 5;
    private static final int REASON_MAX = 500;

    private final CreatorRepository creators;
    private final CreatorWithdrawalRequestRepository requests;
    private final CreatorWithdrawalEligibility eligibility;
    private final CreatorAdminHistoryRecorder history;
    private final BusinessCalendar businessCalendar;

    @Transactional
    public CreatorWithdrawalRequest request(Long creatorId, String reasonText, LocalDateTime now) {
        Creator creator = creators.findForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        String reason = reasonText == null ? "" : reasonText.strip();
        if (reason.isEmpty()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_REASON_REQUIRED);
        }
        if (reason.length() > REASON_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴 사유는 500자 이내로 입력해 주세요.");
        }
        if (requests.existsByCreatorIdAndStatus(creatorId, CreatorWithdrawalStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_ALREADY_PENDING);
        }
        CreatorWithdrawalEligibility.Result result = eligibility.evaluate(creatorId);
        if (!result.eligible()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_CONDITION_UNMET,
                    "탈퇴 조건이 충족되지 않았습니다 — " + result.summary());
        }
        CreatorWithdrawalRequest saved = requests.save(CreatorWithdrawalRequest.request(creatorId, reason, now,
                businessCalendar.addBusinessDays(now.toLocalDate(), APPROVAL_DUE_BUSINESS_DAYS)));
        history.byCreator(creatorId, CreatorAdminEventType.WITHDRAWAL_REQUESTED, reason,
                CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST, saved.getId(), now);
        return saved;
    }

    /** 운영자 처리 전까지 인플루언서가 직접 취소한다 — 대기 중 제약도 함께 풀린다(열린 신청 행의 유무로 판정하므로). */
    @Transactional
    public void cancel(Long creatorId, Long requestId, LocalDateTime now) {
        CreatorWithdrawalRequest request = requests.findForUpdate(requestId)
                .filter(r -> r.getCreatorId().equals(creatorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_FOUND));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_NOT_PENDING);
        }
        request.cancel(now);
        history.byCreator(creatorId, CreatorAdminEventType.WITHDRAWAL_CANCELED, null,
                CreatorAdminHistoryRecorder.REF_WITHDRAWAL_REQUEST, requestId, now);
    }
}
