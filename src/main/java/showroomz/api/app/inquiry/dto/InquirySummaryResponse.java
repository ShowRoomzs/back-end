package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 문의 내역 화면(C12) 상단 탭 배지용 건수.
 * 탭을 눌러 보기 전에도 어느 쪽에 내역이 있는지 보여야 하므로 두 종류를 한 번에 내려준다.
 */
@Getter
@Builder
@Schema(description = "문의 내역 탭 건수 — [1:1 문의 N] [상품 문의 N]")
public class InquirySummaryResponse {

    @Schema(description = "1:1 문의 전체 건수", example = "4")
    private long oneToOneTotal;

    @Schema(description = "1:1 문의 중 답변 대기 건수", example = "1")
    private long oneToOneWaiting;

    @Schema(description = "상품 문의 전체 건수", example = "3")
    private long productTotal;

    @Schema(description = "상품 문의 중 답변 대기 건수", example = "1")
    private long productWaiting;
}