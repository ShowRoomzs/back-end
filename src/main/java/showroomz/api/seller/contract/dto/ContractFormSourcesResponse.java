package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 작성 폼의 드롭다운 선택지(설계서 4-1).
 *
 * <p>기존 {@code /v1/seller/connections}·{@code /v1/seller/products}를 쓰지 않는 이유:
 * 둘은 목록용이라 페이징·필터가 계약 폼과 다르다. 드롭다운은 「연결됨 상대 전량 +
 * 진열 상품 전량」이 한 번에 필요하다.
 */
@Schema(description = "계약 작성 폼 선택지")
public record ContractFormSourcesResponse(

        @Schema(description = "연결됨(CONNECTED) 상태인 계약 상대 전량")
        List<Counterparty> counterparties,

        @Schema(description = "진열(DISPLAY) 상태인 내 상품 전량 — 정가를 동봉해 공구가 입력 즉시 "
                + "할인율·H1(공구가 > 정가)을 화면에서 먼저 비출 수 있게 한다")
        List<ProductOption> products
) {

    @Schema(description = "선택 가능한 계약 상대")
    public record Counterparty(
            Long creatorId,
            @Schema(example = "글로우_지민") String showroomName,
            @Schema(description = "연결 ID — 계약 생성 시 함께 보내면 「스레드 열기」 딥링크의 출처가 된다")
            Long connectionId,
            @Schema(nullable = true) String profileImageUrl
    ) {
    }

    @Schema(description = "선택 가능한 상품")
    public record ProductOption(
            Long productId,
            String productName,
            @Schema(description = "현재 정가 — 계약 항목에는 이 값이 스냅샷으로 복사된다") Integer regularPrice,
            @Schema(nullable = true) String thumbnailUrl
    ) {
    }
}
