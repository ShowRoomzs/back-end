package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인플루언서 정산 계좌 확인 상태(50 설계서 1-2 · 1-3). 「실명 확인」= 토스 셀러 등록 · 수정의 계좌 검증이다 — 예금주 조회 API 는 없다.
 * 확인 중 · 실패 · 변경 필요면 인플루언서 몫 지급이 보류된다({@code PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED}). 확인 이력이 없는
 * 기존 회원({@link #UNVERIFIED})은 막지 않는다 — 확정 · 지시 때의 PG 동기화가 같은 검증을 한다.
 */
@Getter
@RequiredArgsConstructor
public enum CreatorAccountVerificationStatus {
    UNVERIFIED("확인 전", "NEUTRAL"),
    /** 1021 기-22 — 라벨 「운영자 확인」 계열(값 유지). 승인 뒤 PG 계좌 실존 확인이 도는 동안이다. */
    VERIFYING("운영자 확인 중", "INFO"),
    VERIFIED("운영자 확인 완료", "SUCCESS"),
    FAILED("확인 실패", "WARNING"),
    /** 사업자 구분 변경 뒤 예금주 규칙 위반(T10) — 상호 명의 계좌를 받기 전까지(§55 B-9)는 생기지 않는다. */
    CHANGE_REQUIRED("변경 필요", "WARNING");

    private final String label;
    private final String tone;

    public boolean blocksPayout() {
        return this == VERIFYING || this == FAILED || this == CHANGE_REQUIRED;
    }
}
