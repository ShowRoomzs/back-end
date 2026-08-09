package showroomz.api.seller.changerequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.changerequest.type.ChangeRequestField;

/** GET /fields — M1·M2 모달 진입용. 항목 목록·라벨·현재값의 SoT는 서버 enum이다. */
@Getter
@Builder
@Schema(description = "변경 요청 가능 항목")
public class ChangeRequestFieldOption {
    private ChangeRequestField fieldKey;
    private String label;
    private String currentValue;
}
