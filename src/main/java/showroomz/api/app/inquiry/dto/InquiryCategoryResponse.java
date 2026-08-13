package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 1:1 문의 유형 옵션 (§17-2-1) — 소분류 없이 5종 단일 레벨이다. */
@Getter
@AllArgsConstructor
@Schema(description = "1:1 문의 유형 옵션")
public class InquiryCategoryResponse {

    @Schema(description = "유형 코드", example = "DELIVERY")
    private String key;

    @Schema(description = "유형 명", example = "배송")
    private String description;
}
