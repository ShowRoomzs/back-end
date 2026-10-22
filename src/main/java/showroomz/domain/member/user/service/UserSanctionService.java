package showroomz.domain.member.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 소비자 계정 정지 · 해제(§51-1 · §25-4 · 51-6 설계서 2절) — 회원 행을 잠그고 한 트랜잭션에서 정지 행 · 회원 상태 · 전이 로그 · 처리 이력을
 * 함께 바꾼다. 알림만 커밋 뒤다. 운영자 이름은 호출자(어드민 서비스)가 확인해 넘긴다 — 도메인이 api 의 운영자 해석기를 부르지 않는다.
 *
 * <p>{@code user_status_history}는 정지 · 해제 때도 계속 쓴다 — 목록 정지 탭의 「최근 30일 신규 정지」가 그 표를 읽는다(0-2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserSanctionService {

    /** 재가입 복구 전이를 전이 로그에 남길 때의 사유 표식(6절). */
    public static final String REJOIN_REASON = "REJOIN";

    private final UserRepository userRepository;
    private final UserSanctionRepository sanctionRepository;
    private final UserStatusHistoryRepository statusHistoryRepository;
    private final UserAdminHistoryRecorder historyRecorder;
    private final UserSanctionNotifier notifier;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;

    public UserSanction suspend(Long userId, UserSuspensionReason reason, String reasonDetail, Long operatorId,
                                String operatorName, LocalDateTime now) {
        return suspend(userId, reason, reasonDetail, null, operatorId, operatorName, now);
    }

    /** 정지 — 종료일(포함)이 있으면 다음 날 00:00 자동 해제 · 이의 신청 기한 = 통지 + N영업일(1021 기-02). */
    public UserSanction suspend(Long userId, UserSuspensionReason reason, String reasonDetail, java.time.LocalDate endsOn,
                                Long operatorId, String operatorName, LocalDateTime now) {
        java.time.LocalDate term = termPolicy.requireEndsOn(endsOn, now);
        if (reason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정지 사유를 선택해 주세요.");
        }
        String detail = normalize(reasonDetail);
        if (reason.isDetailRequired() && detail == null) {
            throw new BusinessException(ErrorCode.USER_SUSPEND_DETAIL_REQUIRED);
        }
        Users user = lockConsumer(userId);
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_SANCTION_WITHDRAWN);
        }
        if (user.getStatus() == UserStatus.SUSPENDED || sanctionRepository.existsByUserIdAndReleasedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_SUSPENDED);
        }

        UserSanction sanction = sanctionRepository.save(
                UserSanction.suspend(userId, reason, detail, operatorId, operatorName, now));
        sanction.applyTerm(term, termPolicy.appealDeadline(now));
        transition(user, UserStatus.SUSPENDED, reason.name());
        historyRecorder.byOperator(userId, UserAdminEventType.SUSPENDED, operatorId, operatorName, sanction.reasonText(),
                UserAdminHistoryRecorder.REF_SANCTION, sanction.getId(), now);
        String appLabel = reason.getAppLabel();
        AfterCommit.run(() -> notifier.suspended(userId, appLabel));
        log.info("소비자 정지 - userId: {}, reason: {}", userId, reason);
        return sanction;
    }

    /**
     * 해제 — 회원 상세에서만(§51-4). 사유는 필수 · 상세 없음(§25-4). 정지 행 없이 정지 상태인 회원(옛 {@code PATCH /status} 로 정지 —
     * V204 는 사유를 지어내지 않는다)도 풀 수 있어야 한다 — 그때는 상태만 되돌린다.
     */
    public UserSanction release(Long userId, UserReleaseReason reason, Long operatorId, String operatorName,
                                LocalDateTime now) {
        if (reason == null) {
            throw new BusinessException(ErrorCode.USER_RELEASE_REASON_REQUIRED);
        }
        Users user = lockConsumer(userId);
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_SANCTION_WITHDRAWN);
        }
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_NOT_SUSPENDED);
        }
        UserSanction sanction = sanctionRepository.findByUserIdAndReleasedAtIsNull(userId).orElse(null);
        if (sanction != null) {
            sanction.release(reason, operatorId, operatorName, now);
        }
        transition(user, UserStatus.NORMAL, reason.name());
        if (operatorId == null) {
            historyRecorder.bySystem(userId, UserAdminEventType.SUSPENSION_RELEASED, reason.getLabel(),
                    UserAdminHistoryRecorder.REF_SANCTION, sanction == null ? null : sanction.getId(), now);
        } else {
            historyRecorder.byOperator(userId, UserAdminEventType.SUSPENSION_RELEASED, operatorId, operatorName,
                    reason.getLabel(), UserAdminHistoryRecorder.REF_SANCTION, sanction == null ? null : sanction.getId(),
                    now);
        }
        AfterCommit.run(() -> notifier.released(userId));
        log.info("소비자 정지 해제 - userId: {}, reason: {}", userId, reason);
        return sanction;
    }

    /** 정지 기간 종료 자동 해제(1021 기-02) — 시스템 · 사유 「제재 기간 종료」. 기간이 없거나 남았거나 이미 풀렸으면 null. */
    public UserSanction releaseByTerm(Long userId, LocalDateTime now) {
        UserSanction open = sanctionRepository.findByUserIdAndReleasedAtIsNull(userId).orElse(null);
        if (open == null || !open.isTermEnded(now.toLocalDate())) {
            return null;
        }
        UserSanction released = release(userId, UserReleaseReason.TERM_ENDED, null, null, now);
        if (released != null) {
            released.markAutoReleased();
        }
        return released;
    }

    /**
     * 탈퇴 회원이 같은 소셜 계정으로 돌아왔다(51-6 설계서 6절) — 열린 정지가 있으면 {@code SUSPENDED}로, 없으면 {@code NORMAL}로 되돌린다.
     * 정지 중 탈퇴는 정지 행을 닫지 않으므로 「탈퇴 → 재가입」 한 번으로 정지가 풀리지 않는다(§51-3 의 소비자판). 되살아난 정지는 안내
     * 시트를 다시 띄운다. 호출자 트랜잭션 안에서만 쓴다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserStatus restoreOnRejoin(Users user) {
        UserSanction open = sanctionRepository.findByUserIdAndReleasedAtIsNull(user.getId()).orElse(null);
        UserStatus restored = open == null ? UserStatus.NORMAL : UserStatus.SUSPENDED;
        if (open != null) {
            open.resetNotice();
        }
        transition(user, restored, REJOIN_REASON);
        log.info("탈퇴 회원 재가입 복구 - userId: {}, status: {}", user.getId(), restored);
        return restored;
    }

    private void transition(Users user, UserStatus to, String reason) {
        UserStatus from = user.getStatus();
        user.updateStatus(to);
        statusHistoryRepository.save(UserStatusHistory.builder()
                .user(user)
                .previousStatus(from)
                .newStatus(to)
                .reason(reason)
                .build());
    }

    /** 어드민 소비자 목록과 같은 모집단만 조치 대상이다(0-1) — 인플루언서는 §53 이 {@code creator.status}로 정지한다. */
    private Users lockConsumer(Long userId) {
        Users user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRoleType() != RoleType.USER) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
