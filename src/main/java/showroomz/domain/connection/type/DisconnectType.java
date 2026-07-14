package showroomz.domain.connection.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 해제 사유 구분 — 연결요청 단계의 거절인지, 연결됨 이후의 해제인지.
 */
@Getter
@RequiredArgsConstructor
public enum DisconnectType {
    REJECTED("거절"),
    RELEASED("해제");

    private final String description;
}
