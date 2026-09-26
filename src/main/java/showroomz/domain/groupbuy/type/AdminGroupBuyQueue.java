package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 조치 큐 4종 — 「내가 눌러야 다음으로 가는 것」(32 설계 2-3). 네 큐는 서로 배타적이다.
 * 게시물 숨김(다음 차례 인플루언서) · 소명 대기(브랜드) · 이행 미합의(3자 스레드)는 넣지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum AdminGroupBuyQueue {
    OPEN_REVIEW("오픈 승인"),
    SUSPEND_REQUEST("중단 요청"),
    EARLY_CLOSE_REQUEST("조기 마감 요청"),
    /** 소명 제출됨 ∨ 소명 기한 경과 — 기한 경과 미제출도 판정자는 운영자다(제17조④ · 13-1 #20). */
    APPEAL_REVIEW("소명 검토");

    private final String label;
}
