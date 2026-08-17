package showroomz.api.app.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * C1 홈 헤더·빈 상태 분기에 필요한 값 묶음.
 *
 * <p>헤더의 장바구니 배지 하나 때문에 장바구니 <b>전체</b>({@code GET /v1/user/cart})를 부르지
 * 않게 하려고 따로 뒀다. 홈은 앱을 열 때마다 그려지는 화면이라, 배지 숫자를 얻자고 담긴 상품의
 * 옵션·재고·배송비까지 매번 계산하면 가장 자주 오는 요청이 가장 무거운 요청이 된다.
 *
 * <p>{@code followingCount}가 같이 실리는 것도 같은 이유다. 팔로잉 피드를 한 번 불러 비었는지
 * 보고 나서야 빈 상태를 그리면 화면이 한 번 깜빡인다. 첫 응답에서 어느 화면을 그릴지 정할 수 있다.
 */
@Schema(description = "C1 홈 요약 — 헤더 배지와 빈 상태 분기에 필요한 값")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeSummaryResponse {

    /**
     * 디자인의 배지는 1~99까지만 적고 그 위는 99+로 줄인다. 자르는 것은 표기 규칙이라
     * 클라이언트가 하고, 서버는 실제 수를 그대로 준다 — 잘린 값을 받으면 앱이 다른 화면에서
     * 같은 숫자를 다시 쓸 수 없다.
     */
    @Schema(description = "장바구니에 담긴 상품 수 — 0이면 배지를 그리지 않는다. 99를 넘으면 앱이 99+로 표기한다",
            example = "3")
    private Long cartCount;

    /**
     * C1 빈 상태 판정 기준. "팔로잉 피드가 비었다"가 아니라 <b>"팔로우한 쇼룸이 없다"</b>가
     * 기준이다 — 팔로우는 했지만 아직 아무도 글을 안 올린 경우에 "팔로우한 쇼룸이 없어요"라고
     * 말하면 거짓이 된다.
     */
    @Schema(description = "팔로우 중인 쇼룸 수 — 0이면 빈 상태(발견 피드)를 그린다", example = "12")
    private Long followingCount;
}
