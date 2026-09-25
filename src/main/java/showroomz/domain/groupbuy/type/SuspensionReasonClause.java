package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사전 통지 직권 중단의 근거 — 제17조① 1~4호 고정. <b>ETC가 없다</b> — 약관에 없는 사유로 판매를 끊을 수 없다(§29-7).
 * 자유 사유를 쓰면 제25조 제재 판정에 쓸 수 없다. 라벨은 어드민 상세·이력 detail이 쓴다(32 설계 4-6).
 */
@Getter
@RequiredArgsConstructor
public enum SuspensionReasonClause {
    ART17_1_LAW("제17조① 1호 법령 위반"),
    ART17_2_IP_DEFECT("제17조① 2호 지식재산권 침해·중대 하자"),
    /** 「게시물 무단 변경」은 브랜드가 소명할 수 없는 사유다(C-2) — 막지 않고 통지 응답에 주의를 싣는다(32 설계 6-2). */
    ART17_3_BREACH("제17조① 3호 중대 의무 불이행"),
    ART17_4_DISPUTE("제17조① 4호 분쟁 심화·신용 훼손");

    private final String label;
}
