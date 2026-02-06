package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InquiryStatus {

    WAITING("답변 대기"),
    ANSWERED("답변 완료");

    private final String description;
}
