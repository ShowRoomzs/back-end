package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 환불 방법(1021 설계서 4-5) — PG 취소 · 원래 결제액 초과분의 운영자 계좌 송금(기록만 · 시스템은 송금하지 않는다). */
@Getter
@RequiredArgsConstructor
public enum RefundMethod {
    PG_CANCEL("PG 취소"),
    BANK_TRANSFER("계좌 송금");

    private final String label;
}
