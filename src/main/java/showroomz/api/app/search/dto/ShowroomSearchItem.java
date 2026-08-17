package showroomz.api.app.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * C14 쇼룸 검색 결과 한 행.
 *
 * <p>행 전체가 C4 쇼룸으로 가는 단일 액션이므로 팔로우 버튼·팔로워 수·한 줄 소개는 담지 않는다.
 * 표시는 이름 + 아이디(@handle) 2줄이며, 일치 구간 하이라이트는 클라이언트가 검색어로 계산한다.
 */
@Getter
@Builder
@Schema(description = "쇼룸 검색 결과 항목")
public class ShowroomSearchItem {

    @Schema(description = "쇼룸(크리에이터) ID — 탭하면 이 ID로 C4 쇼룸 진입", example = "5")
    private final Long showroomId;

    @Schema(description = "쇼룸명", example = "브라이튼 룸")
    private final String showroomName;

    @Schema(description = "쇼룸 아이디(@handle) — 쇼룸 고유값, 중복 불가", example = "brighten_room")
    private final String showroomAddress;

    @Schema(description = "쇼룸 프로필 이미지 URL — 없으면 null(기본 이미지)",
            example = "https://cdn.showroomz.co.kr/showroom/profile/abc.jpg", nullable = true)
    private final String showroomImageUrl;

    @Schema(description = "진행 중인 공구 보유 여부 (아바타 로즈 링 표시용)", example = "true")
    private final Boolean hasOngoingGroupBuy;
}
