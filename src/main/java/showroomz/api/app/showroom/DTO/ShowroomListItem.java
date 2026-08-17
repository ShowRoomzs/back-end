package showroomz.api.app.showroom.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 쇼룸 목록 한 행.
 *
 * <p>구 {@code MarketListResponse}(샵 목록)를 대체한다. 마켓은 소비자에게 조회되지 않으므로
 * {@code shopType} 판별자가 사라졌고, 대표 카테고리도 없다 — 카테고리는 마켓이 가진 속성이지
 * 쇼룸의 속성이 아니다.
 *
 * <p>{@code hasOngoingGroupBuy}는 아바타 로즈 링(§02 · C1·C2·C14 공통 규칙)이고,
 * {@code isFollowing}은 팔로우 버튼 노출 여부다 — 이미 팔로우한 쇼룸에는 버튼을 그리지 않는다.
 */
@Getter
@Builder
@Schema(description = "쇼룸 목록 항목")
public class ShowroomListItem {

    @Schema(description = "쇼룸(크리에이터) ID — 탭하면 이 ID로 C4 쇼룸 진입", example = "5")
    private final Long showroomId;

    @Schema(description = "쇼룸명", example = "제니의 뷰티룸")
    private final String showroomName;

    @Schema(description = "쇼룸 아이디(@handle) — 쇼룸 고유값, 중복 불가", example = "jenny_beautyroom")
    private final String showroomAddress;

    @Schema(description = "쇼룸 프로필 이미지 URL — 없으면 null(기본 이미지)",
            example = "https://cdn.showroomz.co.kr/showroom/profile/abc.jpg", nullable = true)
    private final String showroomImageUrl;

    @Schema(description = "쇼룸 소개 한 줄 — 미등록이면 null", example = "매일 쓰는 것만 소개합니다", nullable = true)
    private final String introduction;

    @Schema(description = "진행 중인 공구 보유 여부 (아바타 로즈 링 표시용)", example = "true")
    private final Boolean hasOngoingGroupBuy;

    @Schema(description = "현재 사용자의 팔로우 여부 — false일 때만 팔로우 버튼을 그린다. 비로그인은 항상 false",
            example = "false")
    private final Boolean isFollowing;
}
