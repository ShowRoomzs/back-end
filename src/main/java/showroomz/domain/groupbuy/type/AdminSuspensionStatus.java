package showroomz.domain.groupbuy.type;

/**
 * 직권 중단 통지의 진행. 긴급(EMERGENCY)은 NOTICED를 거치지 않고 EXECUTED로 바로 생성된다.
 * {@link #LAPSED}는 집행 전에 공구 기간이 끝난 경우다(설계서 7-2 #3).
 */
public enum AdminSuspensionStatus {
    NOTICED, WITHDRAWN, EXECUTED, LAPSED
}
