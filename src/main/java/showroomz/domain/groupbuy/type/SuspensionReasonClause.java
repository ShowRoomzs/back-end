package showroomz.domain.groupbuy.type;

/**
 * 사전 통지 직권 중단의 근거 — 제17조① 1~4호 고정.
 * 자유 사유를 쓰면 제25조 제재 판정에 쓸 수 없다(§29-7). 표시 문구는 FE가 호수로 고른다.
 */
public enum SuspensionReasonClause {
    ART17_1_LAW,
    ART17_2_IP_DEFECT,
    ART17_3_BREACH,
    ART17_4_DISPUTE
}
