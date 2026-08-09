package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AttachmentStatus {
    PENDING("presign만 발급"),
    UPLOADED("HeadObject 검증 통과"),
    REJECTED("HeadObject 불일치 — S3 객체는 삭제, 행은 감사·정리용으로 보존");

    private final String description;
}
