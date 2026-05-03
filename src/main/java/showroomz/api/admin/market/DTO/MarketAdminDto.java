package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class MarketAdminDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 관리자 메모 수정 요청")
    public static class UpdateAdminMemoRequest {

        @Schema(
                description = "관리자 내부 메모 (마켓 운영 상태, CS 이슈 등 기록용)",
                example = "배송 지연으로 인한 1차 경고 발송 (2026.05.03)"
        )
        private String adminMemo;
    }
}
