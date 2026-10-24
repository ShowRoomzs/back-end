package showroomz.api.admin.home.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 어드민 홈 할 일의 급한 정도(1017 설계서 6-0 ②) — 선언 순서가 정렬 순서다. */
@Getter
@RequiredArgsConstructor
public enum TodoUrgency {
    FAILED("실패"),
    OVERDUE("기한 초과"),
    EXECUTION_PENDING("집행 대기"),
    DUE_SOON("기한 임박"),
    WAITING("대기");

    private final String label;
}
