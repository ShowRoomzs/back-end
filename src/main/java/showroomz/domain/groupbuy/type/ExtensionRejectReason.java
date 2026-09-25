package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 기간 연장 거절 사유(C2) — 선택이다. 「기타(직접 입력)」면 메모가 필수다(31 설계 5-2). */
@Getter
@RequiredArgsConstructor
public enum ExtensionRejectReason {
    NEXT_SCHEDULE_BOOKED("다음 일정이 잡혀 있음"),
    CONTENT_PLAN_MISMATCH("콘텐츠 계획과 맞지 않음"),
    TERMS_RENEGOTIATION("조건 재협의 필요"),
    ETC("기타");

    private final String label;

    public boolean requiresMemo() {
        return this == ETC;
    }
}
