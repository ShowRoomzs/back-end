package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.common.AdminPrivilege;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.user.entity.UserPrivacyAccessLog;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserPrivacyAccessLogRepository;
import showroomz.domain.member.user.service.UserAdminHistoryRecorder;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserPrivacyAccessReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 소비자 본인확인 정보 마스킹 해제(ui-admin-13 M2 · 55 계획서 M3-3) — 흐름은 인플루언서 {@code AdminCreatorPrivacyService}와 같다. 대상 =
 * 이름 · 생년월일 · 휴대폰 + 배송지 상세(시안 A4). 범위 = 이 소비자 1건 · 이 운영자 · 10분 고정 만료 · [지금 잠금]. 열람 사실은 사유와 함께
 * 처리 이력에 즉시 남는다.
 *
 * <p>같은 운영자의 유효 세션이 있으면 새 행을 만들지 않는다(두 번 눌러도 로그 1건). 「다른 소비자로 이동 · 새로고침이면 다시 마스킹」은 FE 가
 * 화면을 떠날 때 {@code privacy-lock}을 부르고, 서버는 세션을 회원 1건 · 운영자 1명에 묶어 다른 상세에 실값이 새지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserPrivacyService {

    private final UserPrivacyAccessLogRepository logs;
    private final UserAdminHistoryRecorder history;
    private final AdminOperatorResolver operators;
    private final AdminPrivilege privilege;
    private final UserRepository userRepository;
    private final AdminUserService userService;

    /** 해제 — 탈퇴(파기) 회원은 409 · 권한 없으면 403. 해제된 상세 전체로 답한다. */
    public AdminUserDto.UserDetailResponse unmask(Long userId, Long operatorId, UserPrivacyAccessReason reason) {
        String operatorName = operators.operatorName(operatorId);
        Users user = load(userId);
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_PRIVACY_UNMASK_NOT_AVAILABLE);
        }
        if (!privilege.canUnmaskPrivacy(operatorId)) {
            throw new BusinessException(ErrorCode.USER_PRIVACY_UNMASK_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean active = logs.findFirstByUserIdAndOperatorIdOrderByAccessedAtDescIdDesc(userId, operatorId)
                .filter(log -> log.isActive(now))
                .isPresent();
        if (!active) {
            UserPrivacyAccessLog log = logs.save(UserPrivacyAccessLog.open(userId, operatorId, reason, now));
            history.byOperator(userId, UserAdminEventType.PRIVACY_UNMASKED, operatorId, operatorName, reason.getLabel(),
                    UserAdminHistoryRecorder.REF_PRIVACY_ACCESS, log.getId(), now);
        }
        return userService.getUserDetail(userId, operatorId);
    }

    /** [지금 잠금] — 이 운영자의 유효 세션을 닫는다. 없어도 성공(멱등). */
    public void lock(Long userId, Long operatorId) {
        operators.operatorName(operatorId);
        load(userId);
        LocalDateTime now = LocalDateTime.now();
        // 잠금은 쓰기 — 읽기 전용 조립기를 거치지 않고 이 트랜잭션에서 직접 읽어 닫는다
        logs.findFirstByUserIdAndOperatorIdOrderByAccessedAtDescIdDesc(userId, operatorId)
                .filter(log -> log.isActive(now))
                .ifPresent(log -> log.lock(now));
    }

    private Users load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
