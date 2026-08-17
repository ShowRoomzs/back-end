package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 처리 이력의 행위 주체 — 이력 라벨(작성자 표기)을 이 값으로 결정한다. */
@Getter
@AllArgsConstructor
public enum InquiryActorType {

    CONSUMER("소비자"),
    BRAND("브랜드"),
    OPERATOR("운영자");

    private final String description;
}
