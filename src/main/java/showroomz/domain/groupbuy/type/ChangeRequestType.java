package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 운영자 승인이 필요한 요청 2종(§29-6). 승인권자·검토 흐름이 같고 결과 상태만 다르다. */
@Getter
@RequiredArgsConstructor
public enum ChangeRequestType {
    SUSPEND("공구 중단"),
    EARLY_CLOSE("조기 마감");

    private final String label;
}
