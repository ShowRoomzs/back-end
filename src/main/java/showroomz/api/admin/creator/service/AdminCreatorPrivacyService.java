package showroomz.api.admin.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.common.AdminPrivilege;
import showroomz.api.admin.creator.dto.AdminCreatorDto.Detail;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorPrivacyAccessLog;
import showroomz.domain.member.creator.repository.CreatorPrivacyAccessLogRepository;
import showroomz.domain.member.creator.service.CreatorAdminHistoryRecorder;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorPrivacyAccessReason;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 본인확인 정보 마스킹 해제(M4 · §53-2 · 53 설계서 3-8). 대상 = 이름 · 생년월일 · 휴대폰 — <b>계좌 · CI/DI 는 해제 대상이 아니다.</b>
 * 범위 = 이 인플루언서 1건 · 이 운영자 · 10분 고정 만료 · [지금 잠금]. 열람 사실은 사유와 함께 처리 이력에 즉시 남는다.
 *
 * <p>같은 운영자의 유효 세션이 있으면 새 행을 만들지 않는다(두 번 눌러도 로그 1건). 「다른 회원으로 이동하면 다시 마스킹」은 FE 가 화면을
 * 떠날 때 {@code privacy-lock}을 부르고, 서버는 세션을 회원 1건에 묶어 다른 회원의 상세에 실값이 새지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminCreatorPrivacyService {

    private final CreatorPrivacyAccessLogRepository logs;
    private final CreatorAdminHistoryRecorder history;
    private final AdminOperatorResolver operators;
    private final AdminPrivilege privilege;
    private final AdminCreatorQueryService queryService;

    public Detail unmask(Long creatorId, Long operatorId, CreatorPrivacyAccessReason reason) {
        String operatorName = operators.operatorName(operatorId);
        Creator creator = queryService.load(creatorId);
        if (creator.isPrivacyPurged()) {
            throw new BusinessException(ErrorCode.CREATOR_PRIVACY_UNMASK_NOT_AVAILABLE);
        }
        if (!privilege.canUnmaskPrivacy(operatorId)) {
            throw new BusinessException(ErrorCode.CREATOR_PRIVACY_UNMASK_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean active = logs.findFirstByCreatorIdAndOperatorIdOrderByAccessedAtDescIdDesc(creatorId, operatorId)
                .filter(log -> log.isActive(now))
                .isPresent();
        if (!active) {
            CreatorPrivacyAccessLog log = logs.save(CreatorPrivacyAccessLog.open(creatorId, operatorId, reason, now));
            history.byOperator(creatorId, CreatorAdminEventType.PRIVACY_UNMASKED, operatorId, operatorName,
                    reason.getLabel(), CreatorAdminHistoryRecorder.REF_PRIVACY_ACCESS, log.getId(), now);
        }
        return queryService.assemble(creator, operatorId, now);
    }

    /** [지금 잠금] — 이 운영자의 유효 세션을 닫는다. 없어도 성공(멱등). */
    public void lock(Long creatorId, Long operatorId) {
        operators.operatorName(operatorId);
        queryService.load(creatorId);
        LocalDateTime now = LocalDateTime.now();
        logs.findFirstByCreatorIdAndOperatorIdOrderByAccessedAtDescIdDesc(creatorId, operatorId)
                .filter(log -> log.isActive(now))
                .ifPresent(log -> log.lock(now));
    }
}
