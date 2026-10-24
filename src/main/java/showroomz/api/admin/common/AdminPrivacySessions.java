package showroomz.api.admin.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.member.common.entity.PrivacyAccessSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 본인확인 정보 마스킹 해제 상태 조립 — 인플루언서 · 소비자 상세가 같은 규칙을 쓴다(55 계획서 M3-3 · 53 설계서 3-8). 카드 헤더의 「해제됨 ·
 * 처리자 · 시각 · N 후 잠금」과 해제 버튼 활성을 한 곳에서 정한다. 세션 규칙(유효 · 잠금 · 10분)은 {@link PrivacyAccessSession}이다.
 *
 * <ul>
 *   <li>버튼 활성 = 파기 회원 아님 ∧ 해제 권한({@link AdminPrivilege} · 최고관리자 한정 예정)</li>
 *   <li>실값 = 이 운영자의 유효한 세션이 있고 파기 회원이 아님 — 다른 운영자의 세션은 적용되지 않는다(호출자가 이 운영자 세션만 넘긴다)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminPrivacySessions {

    private final AdminOperatorResolver operators;
    private final AdminPrivilege privilege;

    /**
     * @param session 이 운영자가 이 회원에 연 가장 최근 세션(없으면 null) — 만료 · 잠금이면 닫힌 상태로 본다
     * @param purged  탈퇴 파기 회원 — 해제할 값이 없다
     */
    public View view(PrivacyAccessSession session, boolean purged, Long operatorId, LocalDateTime now) {
        boolean allowed = !purged && privilege.canUnmaskPrivacy(operatorId);
        if (purged || session == null || !session.isActive(now)) {
            return new View(allowed, false, null, null, null, null);
        }
        String operatorName = operators.namesOf(List.of(session.getOperatorId())).get(session.getOperatorId());
        return new View(allowed, true, operatorName, session.getAccessedAt(), session.getExpiresAt(),
                session.remainingSeconds(now));
    }

    /** 해제 상태 — 회원 유형별 DTO 가 직전 사유(유형별 enum)를 붙여 내린다. */
    public record View(boolean allowed, boolean unmasked, String operatorName, LocalDateTime unmaskedAt,
                       LocalDateTime expiresAt, Long remainingSeconds) {
    }
}
