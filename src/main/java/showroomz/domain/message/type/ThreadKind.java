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
    /** 공구 이슈 스레드(30 설계 C5 · 32 설계 8-3) — 이슈마다 새 스레드다. */
    GROUP_BUY_ISSUE("공구 이슈"),
    /** 공구 미이행 3자 스레드(제20조②③ · 30 설계 C7) — 공구당 1개, 양측 미이행이 한 스레드로 모인다. */
    GROUP_BUY_FULFILLMENT("공구 이행 이견");

    private final String description;

    public boolean isGroupBuy() {
        return this != CONNECTION;
    }
}
