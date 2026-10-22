package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 해지 요청 상태(1021 설계서 1-4). 검토 중 요청은 계약당 1건. */
@Getter
@RequiredArgsConstructor
public enum ContractTerminationRequestStatus {
    PENDING("처리 대기"),
    /** 운영자가 해지 처리했다. */
    HANDLED("해지 처리됨"),
    /** 요청자가 처리 전에 거뒀다. */
    WITHDRAWN("요청 철회"),
    /** 공구가 오픈되는 등 해지할 수 없게 되어 닫혔다 — 오픈 뒤는 공구 중단 요청으로. */
    LAPSED("자동 종료");

    private final String label;
}
