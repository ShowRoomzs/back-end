package showroomz.api.seller.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 공개여부 필터 (§23-2) — 상태가 아니라 분류다. 필터 패널에서 다중선택한다. */
@Getter
@AllArgsConstructor
public enum InquiryVisibility {

    PUBLIC("공개", false),
    SECRET("비밀글", true);

    private final String description;
    private final boolean secret;
}
