package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 고정 지급비 지급 시점 — 정의 툴팁은 1021 결정 노트 기-28(파트너 08 · 어드민 01). */
@Getter
@RequiredArgsConstructor
public enum FixedFeeTrigger {
    POST_REGISTERED("공구 게시물 등록 후", "운영자 오픈 승인 후 게시물이 쇼룸에 노출된 때"),
    GROUP_BUY_ENDED("공구 종료 후", "공구가 끝난 때 — 조기 마감 · 중단 포함"),
    SETTLEMENT_COMPLETED("정산 완료 후", "정산 지급이 완료된 때");

    private final String label;
    /** 지급 시점의 정의(툴팁) — 「그 뒤 N영업일 안에 지급」은 설정값({@code contract.fixed-fee-payment-business-days}). */
    private final String description;
}
