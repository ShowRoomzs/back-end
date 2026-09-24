package showroomz.domain.contract.type;

import java.time.LocalDateTime;

/**
 * 서명 기한 임박 판정 — <b>임계값을 한 곳에만 둔다</b>(§27 설계서 미결 #1).
 *
 * <p>시안 S1은 첫 행만 경고색이고 기준을 적지 않았다. §28-8 D #9(서명 기한 임박 알림 D-3 · D-1)가
 * 확정되면 <b>알림 시점과 같은 값</b>을 써야 한다 — 알림이 D-3에 오는데 색은 D-1에 바뀌면
 * 둘 중 하나가 거짓말이 된다. 확정 전까지 D-3으로 집행한다.
 *
 * <p>기한 임박 알림 배치(설계서 8-2 · 미결 #3)가 생기면 그 배치도 이 상수를 읽는다.
 * 그 배치는 <b>상태를 바꾸지 않는다</b> — §25-3의 「만료는 자동 판정이 아니다」는 상태를 자동으로
 * 닫지 말라는 뜻이지 알리지 말라는 뜻이 아니다.
 */
public final class ContractDeadlinePolicy {

    /** 서명 기한 D-3부터 임박으로 본다. */
    public static final int IMMINENT_DAYS = 3;

    private ContractDeadlinePolicy() {
    }

    public static boolean isImminent(LocalDateTime deadlineAt, LocalDateTime now) {
        return deadlineAt != null && !now.isBefore(deadlineAt.minusDays(IMMINENT_DAYS));
    }
}
