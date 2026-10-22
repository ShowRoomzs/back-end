package showroomz.domain.member.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.user.entity.UserAdminHistory;
import showroomz.domain.member.user.repository.UserAdminHistoryRepository;
import showroomz.domain.member.user.type.UserAdminActorType;
import showroomz.domain.member.user.type.UserAdminEventType;

import java.time.LocalDateTime;

/**
 * 소비자 처리 이력 기록(51-6 설계서 0-11) — 조치와 <b>같은 트랜잭션</b>에서 쓴다(조치가 롤백되면 이력도 없다). 파생 사건(가입 · 탈퇴 ·
 * 재가입 · 소명 접수)은 저장하지 않는다 — 읽을 때 합친다.
 */
@Component
@RequiredArgsConstructor
public class UserAdminHistoryRecorder {

    public static final String REF_SANCTION = "SANCTION";
    public static final String REF_MEMO = "MEMO";
    public static final String REF_INQUIRY = "INQUIRY";

    private final UserAdminHistoryRepository histories;

    @Transactional(propagation = Propagation.MANDATORY)
    public UserAdminHistory byOperator(Long userId, UserAdminEventType eventType, Long operatorId, String operatorName,
                                       String detail, String refType, Long refId, LocalDateTime now) {
        return histories.save(UserAdminHistory.of(userId, eventType, UserAdminActorType.ADMIN, operatorId, operatorName,
                detail, refType, refId, now));
    }

    /** 시스템 조치 — 정지 기간 종료 자동 해제(1021 기-02). */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserAdminHistory bySystem(Long userId, UserAdminEventType eventType, String detail, String refType, Long refId,
                                     LocalDateTime now) {
        return histories.save(UserAdminHistory.of(userId, eventType, UserAdminActorType.SYSTEM, null, null, detail,
                refType, refId, now));
    }
}
