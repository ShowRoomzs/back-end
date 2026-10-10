package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 스레드의 정체성이 어디서 오는가(30-1 1절). {@code CONNECTION}은 연결 쌍 자체의 대화로 쌍당 1개다.
 * 공구 3자 스레드는 정체성이 공구에서 오므로 같은 쌍에 두 번째 · 세 번째 스레드가 생긴다.
 */
@Getter
@AllArgsConstructor
public enum ThreadKind {
    CONNECTION("연결"),
    /**
     * 공구 이슈 스레드(30 설계 C5 · 32 설계 8-3) — <b>폐기 이력 · 신규 생성 없음</b>(2026-10-06 · 44 이슈 스레드 설계서 7절).
     * 이슈는 정산 조정 요청으로만 열린다({@link #SETTLEMENT_ADJUSTMENT}). 기존 행 때문에 값은 지우지 않는다.
     */
    GROUP_BUY_ISSUE("공구 이슈"),
    /** 공구 미이행 3자 스레드(제20조②③ · 30 설계 C7) — <b>폐기 이력 · 신규 생성 없음</b>(계약 이행 확인 폐기 · 1009 6절). */
    GROUP_BUY_FULFILLMENT("공구 이행 이견"),
    /**
     * 정산 조정 협의 3자 스레드(44 이슈 스레드 설계서 0-4) — 정산 조정 요청이 자동으로 연다. 정체성은 공구에서 온다
     * ({@code subject_id = group_buy_id}) · 그 쌍의 PAIR 연결에 붙는다. 종결 뒤에도 OPEN 으로 남고 전송 가드가 막는다.
     */
    SETTLEMENT_ADJUSTMENT("정산 조정 협의");

    private final String description;

    public boolean isGroupBuy() {
        return this != CONNECTION;
    }
}
