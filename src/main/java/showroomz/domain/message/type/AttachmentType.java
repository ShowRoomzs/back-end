package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AttachmentType {
    IMAGE("이미지"),
    VIDEO("영상"),
    DOCUMENT("문서(압축 포함)");

    private final String description;
}
