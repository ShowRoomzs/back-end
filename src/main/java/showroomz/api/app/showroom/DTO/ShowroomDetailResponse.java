package showroomz.api.app.showroom.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * C4 쇼룸 프로필 영역 — 아바타(로즈 링) · 이름 · 아이디 · 게시물 수/팔로워 수 · 소개 한 줄 · 인스타 링크.
 *
 * <p>구 {@code MarketDetailResponse}(샵 상세)를 대체한다. 사라진 것 — {@code shopType}(마켓이
 * 조회되지 않으므로 판별자가 필요 없다), 대표 카테고리, {@code snsLinks} 배열(쇼룸이 소비자에게
 * 공개하는 채널은 인스타그램 하나다 §22-1).
 *
 * <p>{@code followerCount}는 <b>쇼룸 팔로워 수</b>다. 크리에이터가 가입할 때 신고한 SNS 팔로워 수
 * ({@code Creator.followerCount})와는 다른 값이고, 소비자 화면에는 쇼룸을 팔로우한 사람 수만 나간다.
 */
@Getter
@Builder
@Schema(description = "쇼룸 상세 (C4 프로필)")
public class ShowroomDetailResponse {

    @Schema(description = "쇼룸(크리에이터) ID", example = "5")
    private final Long showroomId;

    @Schema(description = "쇼룸명", example = "제니의 뷰티룸")
    private final String showroomName;

    @Schema(description = "쇼룸 아이디(@handle) — 쇼룸 고유값, 중복 불가", example = "jenny_beautyroom")
    private final String showroomAddress;

    @Schema(description = "쇼룸 프로필 이미지 URL — 없으면 null(기본 이미지)",
            example = "https://cdn.showroomz.co.kr/showroom/profile/abc.jpg", nullable = true)
    private final String showroomImageUrl;

    @Schema(description = "쇼룸 소개 한 줄 — 미등록이면 null. 앱이 \"소개 미등록\" 문구로 대체한다",
            example = "매일 쓰는 것만 소개합니다", nullable = true)
    private final String introduction;

    @Schema(description = "인스타그램 URL — 프로필 우측 채널 버튼. 없으면 버튼을 그리지 않는다",
            example = "https://instagram.com/jenny_beautyroom", nullable = true)
    private final String instagramUrl;

    @Schema(description = "게시물 수 — 게시중인 게시물만 센다", example = "48")
    private final Long postCount;

    @Schema(description = "팔로워 수 — 이 쇼룸을 팔로우한 사용자 수", example = "1204")
    private final Long followerCount;

    @Schema(description = "진행 중인 공구 보유 여부 — false면 아바타 로즈 링과 진행 중 공구 섹션을 모두 감춘다",
            example = "true")
    private final Boolean hasOngoingGroupBuy;

    @Schema(description = "현재 사용자의 팔로우 여부. 비로그인은 항상 false", example = "false")
    private final Boolean isFollowing;
}
