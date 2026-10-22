package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 시스템 카드 종류(36 설계 4절). 제목은 카드 머리줄이자 목록 미리보기 · 구 FE의 대체 텍스트다. */
@Getter
@AllArgsConstructor
public enum MessageCardType {
    /** 계약 관리의 [서명 안내 다시 받기] — 요청자의 운영팀 채널에 자동 등록된다. */
    CONTRACT_RESEND_REQUEST("요청 · 서명 안내 다시 받기"),
    /** 계약 관리의 운영자 [계약 취소] 결과 — 요청이 들어온 채널에 자동 등록된다. */
    CONTRACT_ADMIN_CANCELED("계약 직권 취소 처리됨"),
    /** 계약 취소 · 해지 요청이 들어와 상대방 동의를 확인하는 카드(1021 기-06 · 어드민 20a A8 ②) — 요청하지 않은 쪽 채널. 버튼 없음. */
    CONTRACT_CLOSE_CONSENT_REQUEST("계약 취소 · 해지 동의 확인"),
    /** 운영자 [해지 처리] 결과(1021 기-05) — 양측 채널. */
    CONTRACT_TERMINATED("계약 해지 처리됨"),
    /** 직권 중단 사전 통지 · 소명 요청(1021 기-21 · 어드민 20a A8 ①) — 양측 채널. 버튼 없음(의견은 답글). */
    GROUP_BUY_SUSPENSION_NOTICE("직권 중단 사전 통지 · 소명 요청"),

    // 정산 조정 협의(44 이슈 스레드 설계서 5-1) — 이슈 스레드에만 등록된다. 상태는 참조 제안 행에서 읽는다.
    /** 시스템 — 조정 요청 접수 · 정산 보류 · 이슈 스레드 개설. */
    SETTLEMENT_ADJUSTMENT_OPENED("정산 조정 요청 접수 · 정산 보류 · 이슈 스레드 개설"),
    /** 요청자 — 첫 제안(seq 1). */
    SETTLEMENT_ADJUSTMENT_REQUEST("정산 조정 요청"),
    /** 제안자 — 다른 금액 제안(seq n). */
    SETTLEMENT_ADJUSTMENT_COUNTER("다른 금액 제안"),
    /** 동의한 쪽 — 「누가 언제 무엇에 동의했는지」. */
    SETTLEMENT_ADJUSTMENT_ACCEPTED("동의"),
    /** 반대한 쪽 — 사유 없음(§42-2). */
    SETTLEMENT_ADJUSTMENT_REJECTED("반대"),
    /** 시스템 — 합의 기한 D-1 안내. */
    SETTLEMENT_ADJUSTMENT_DEADLINE_NOTICE("합의 기한 D-1 안내"),
    /** 시스템 — 합의 결과. */
    SETTLEMENT_ADJUSTMENT_AGREED("합의 완료 · 정산 금액 변경 · 이슈 종결"),
    /** 시스템 — 기한 만료 결과. */
    SETTLEMENT_ADJUSTMENT_EXPIRED("합의 기한 경과 · 원래 금액 확정 · 이슈 종결"),
    /** 요청자 철회(1021 기-14). */
    SETTLEMENT_ADJUSTMENT_WITHDRAWN("요청 철회 · 원래 금액 확정 · 이슈 종결"),

    // 계정 이용 정지(§51-4 · 52 설계서 0-12) — 회원 유형에 중립이다. 브랜드 · 인플루언서의 운영팀 채널에 붙고 그 아래 답글이 곧 소명이다.
    /** 운영자 정지 — 사유 · 일시. */
    ACCOUNT_SUSPENDED("계정 이용 정지"),
    /** 운영자 정지 해제. */
    ACCOUNT_SUSPENSION_RELEASED("정지 해제");

    private final String title;

    public boolean isSettlementAdjustment() {
        return name().startsWith("SETTLEMENT_ADJUSTMENT_");
    }

    /** 제안 카드 — 버튼(동의 · 반대 · 다른 금액 제안)이 붙을 수 있는 카드. */
    public boolean isAdjustmentProposal() {
        return this == SETTLEMENT_ADJUSTMENT_REQUEST || this == SETTLEMENT_ADJUSTMENT_COUNTER;
    }
}
