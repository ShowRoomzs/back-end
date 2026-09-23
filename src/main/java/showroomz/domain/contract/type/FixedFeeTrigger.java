package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 고정 지급비 지급 시점. */
@Getter
@RequiredArgsConstructor
public enum FixedFeeTrigger {
    POST_REGISTERED("게시물 등록 후"),
    GROUP_BUY_ENDED("공구 종료 후"),
    SETTLEMENT_COMPLETED("정산 완료 후");

    private final String label;
}
