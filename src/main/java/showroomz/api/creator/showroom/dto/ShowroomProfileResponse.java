package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

/**
 * §22-1 쇼룸 프로필 — <b>소비자에게 공개되는 값만</b> 담는다.
 * 계정·사업자 정보·정산 계좌·활동 채널은 기본정보 관리(#9) 소관이라 여기에 넣지 않는다.
 */
@Getter
@Builder
@Schema(description = "쇼룸 프로필(공개 정보) 조회 응답")
public class ShowroomProfileResponse {

    @Schema(description = "쇼룸(크리에이터) ID", example = "5")
    private final Long creatorId;

    @Schema(description = "쇼룸명 — 소비자 노출", example = "뷰티 소연")
    private final String showroomName;

    @Schema(description = "쇼룸 프로필 이미지 URL — 앱 계정 프로필과 별개, 없으면 null",
            example = "https://cdn.showroomz.co.kr/showroom/profile/abc.jpg", nullable = true)
    private final String profileImageUrl;

    @Schema(description = "쇼룸 주소 핸들 — 자동 생성·수정 불가", example = "beauty_soyeon")
    private final String showroomAddress;

    @Schema(description = "쇼룸 주소 전체 URL — 복사 버튼이 그대로 쓰는 값",
            example = "https://showroomz.shop/@beauty_soyeon")
    private final String showroomUrl;

    @Schema(description = "쇼룸 소개글 — 최대 50자", example = "뷰티 소품을 좋아하는 소연입니다 🌸", nullable = true)
    private final String introduction;

    @Schema(description = "인스타그램 URL — 소비자 노출, 활동 채널(#9)과는 별개 데이터",
            example = "https://www.instagram.com/beauty_soyeon", nullable = true)
    private final String instagramUrl;

    @Schema(description = "연결코드 — 쇼룸별 고정", example = "SRZ4K7M9XQ")
    private final String connectionCode;

    @Schema(description = "연결코드 (재)발급 시각", example = "2026-07-01T10:00:00", nullable = true)
    private final LocalDateTime connectionCodeIssuedAt;

    public static ShowroomProfileResponse of(Creator creator, String showroomUrl) {
        return ShowroomProfileResponse.builder()
                .creatorId(creator.getId())
                .showroomName(creator.getShowroomName())
                .profileImageUrl(creator.getProfileImageUrl())
                .showroomAddress(creator.getShowroomAddress())
                .showroomUrl(showroomUrl)
                .introduction(creator.getIntroduction())
                .instagramUrl(creator.getInstagramUrl())
                .connectionCode(creator.getConnectionCode())
                .connectionCodeIssuedAt(creator.getConnectionCodeIssuedAt())
                .build();
    }
}
