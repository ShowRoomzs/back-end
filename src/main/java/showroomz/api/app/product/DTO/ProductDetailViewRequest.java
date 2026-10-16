package showroomz.api.app.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 상품 상세 조회 적재 요청(49 스튜디오 성과 설계서 1-4 · 4-2) — 상품 상세(C7)가 열릴 때 한 번 호출한다.
 *
 * <p>{@code groupBuyId}는 상세 조회에 넘긴 값을 그대로 보낸다 — 서버가 상세와 <b>같은 판정</b>으로 이 조회를 공구에 묶는다.
 * 그래야 성과의 상품별 「상세 조회」가 상세 화면이 보여 준 공구와 어긋나지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "상품 상세 조회 적재 요청")
public class ProductDetailViewRequest {

    @Schema(description = "진입 화면 — SHOWROOM(쇼룸) · POST(게시물 상품 칩) · DIRECT_LINK(외부 딥링크) · OTHER(홈 · 검색 · 장바구니 등). "
            + "없거나 모르는 값이면 OTHER", example = "POST", nullable = true)
    private String source;

    @Schema(description = "상세 조회에 넘긴 공구 ID — 없으면 서버가 상세와 같은 규칙(판매 중 공구가 정확히 1개)으로 정한다",
            example = "12", nullable = true)
    private Long groupBuyId;

    @Schema(description = "비로그인 조회자의 디바이스 식별자 — **비로그인일 때 필수.** 쇼룸 방문 · 게시물 노출과 같은 값을 써야 "
            + "게시물 귀속이 이어진다", example = "3f7c1c5a-2f2e-4a9a-9a3f-0f2d9f0a1b2c", nullable = true)
    private String visitorId;
}
