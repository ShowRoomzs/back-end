package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 확정 사유(44 어드민 설계서 1-1) — 이력 문구 · 상세 「정산 근거」 · 스튜디오 「자동 확정 MM.DD + 3영업일」의 앞말. */
@Getter
@RequiredArgsConstructor
public enum SettlementConfirmReason {
    AUTO("자동 확정", "확인 기간 경과 · 조정 요청 없음"),
    AGREED("합의 확정", "조정 합의 · 합의 금액"),
    EXPIRED("기한 만료 확정", "합의 기한 경과 · 원래 금액");

    private final String label;
    private final String description;
}
