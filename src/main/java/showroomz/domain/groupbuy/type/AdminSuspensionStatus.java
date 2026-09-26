package showroomz.domain.groupbuy.type;

/**
 * 직권 중단 통지의 진행. 긴급(EMERGENCY)은 NOTICED를 거치지 않고 EXECUTED로 바로 생성된다.
 *
 * <ul>
 *   <li>{@link #LAPSED} — 집행 전에 공구 기간이 끝나 판정할 대상이 사라졌다(30 설계 7-2 #3)</li>
 *   <li>{@link #SUPERSEDED} — 통지 중 운영자가 긴급 집행으로 대체했다(32 설계 6-5). LAPSED와 뜻이 달라
 *       제재 이력에서 가른다</li>
 * </ul>
 */
public enum AdminSuspensionStatus {
    NOTICED, WITHDRAWN, EXECUTED, LAPSED, SUPERSEDED
}
