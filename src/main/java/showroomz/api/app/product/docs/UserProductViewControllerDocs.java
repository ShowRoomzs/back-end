package showroomz.api.app.product.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.product.DTO.ProductDetailViewRequest;

@Tag(name = "User - Product View", description = "상품 상세 조회 적재 API (성과 관리 원천)")
public interface UserProductViewControllerDocs {

    @Operation(
            summary = "상품 상세 조회 적재",
            description = """
                    상품 상세(C7)가 열릴 때 **한 번** 호출한다. 쇼룸 스튜디오 · 파트너센터 **성과 관리**의 상품별 「상세 조회」와
                    공구 게시물 「상품 상세 이동」이 이 기록으로 집계된다.

                    - **인증 불필요** — 비로그인 조회도 포함된다. 토큰이 있으면 사용자 기준으로 집계된다
                    - **visitorId** — 비로그인일 때 필수. **쇼룸 방문 · 게시물 노출 기록과 같은 값**을 써야 게시물 귀속이 이어진다
                    - **source** — 진입 화면: `SHOWROOM` · `POST`(게시물 상품 칩) · `DIRECT_LINK`(외부 딥링크) · `OTHER`
                    - **groupBuyId** — 상세 조회에 넘긴 값 그대로. 서버가 상세와 같은 판정으로 공구를 정한다
                    - 같은 사람이 같은 상품을 다시 봐도 **30분 안이면 세지 않는다**(쇼룸 방문 · 게시물 노출과 같은 규칙)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "적재 완료(또는 30분 안 재조회라 건너뜀)"),
            @ApiResponse(responseCode = "400", description = "비로그인인데 visitorId 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다." }
                                    """)))
    })
    ResponseEntity<Void> recordView(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "상품 ID", example = "101") @PathVariable Long productId,
            @RequestBody(required = false) ProductDetailViewRequest request);
}
