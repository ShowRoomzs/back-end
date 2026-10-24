package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 탈퇴 조건 내역 행의 세 갈래(51 설계서 4절 · 시안 4-G) — 기다리면 풀림 · 내가 해야 풀림 · 운영팀만 풀 수 있음. */
@Getter
@RequiredArgsConstructor
public enum BrandWithdrawalBreakdownGroup {
    WAIT("기다리면 풀림"),
    MY_ACTION("내 응답 필요"),
    OPERATOR("운영팀 문의");

    private final String label;
}
