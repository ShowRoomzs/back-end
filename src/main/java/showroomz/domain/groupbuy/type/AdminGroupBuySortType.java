package showroomz.domain.groupbuy.type;

/** 어드민 목록 정렬(32 설계 2-4). 「상단 고정」은 별도 목록이 아니라 정렬 키 ⓪이다. */
public enum AdminGroupBuySortType {
    /** ⓪ 조치 큐 해당 먼저 ① created_at DESC ② id DESC */
    ACTION_REQUIRED_FIRST,
    /** ① created_at DESC ② id DESC */
    CREATED_DESC,
    /** ⓪ 비종결 먼저 ① 비종결 end_at ASC · 종결 ended_at DESC ② id DESC */
    END_AT_ASC
}
