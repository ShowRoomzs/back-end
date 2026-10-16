package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 브랜드 탈퇴 신청 사유(파트너 06 M5 · 선택) — 「선택하지 않음」은 null 이다. */
@Getter
@RequiredArgsConstructor
public enum BrandWithdrawalReason {
    BUSINESS_CLOSED("사업 종료"),
    LOW_PERFORMANCE("공구 성과 미흡"),
    OPERATION_BURDEN("운영 부담(배송 · CS)"),
    OTHER_CHANNEL_FOCUS("다른 채널 집중"),
    OTHER("기타");

    private final String label;
}
