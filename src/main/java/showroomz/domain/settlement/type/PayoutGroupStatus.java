package showroomz.domain.settlement.type;

/**
 * 지급 묶음 상태(60_KG이니시스_지급대행_전환_계획서.md 3-2 · 5절).
 *
 * <pre>
 * OPEN ─(송금 결과 01)─▶ PAID
 *      ─(송금 실패 · 처음 올린 묶음의 확정 거절)─▶ FAILED
 *      ─(합계 0 · 삭제 접수 · 잔액 부족 정리)─▶ DELETED
 * </pre>
 * 같은 키(수취자 · 지급일 · 차수)에 OPEN 은 하나뿐이다 — 닫힌 묶음이 있으면 새 행은 {@code seq + 1} 묶음으로 간다.
 */
public enum PayoutGroupStatus {
    OPEN,
    DELETED,
    PAID,
    FAILED;

    public boolean isOpen() {
        return this == OPEN;
    }
}
