package showroomz.api.app.recentSearch.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import showroomz.api.app.search.dto.ShowroomSearchItem;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.recentSearch.entitiy.RecentSearch;
import showroomz.domain.recentSearch.type.RecentSearchType;

import java.time.Instant;
import java.util.Set;

/**
 * C14 최근 검색 한 행 — 쇼룸과 검색어가 한 목록에 시간순으로 섞인다.
 * 행 종류는 {@code type}으로 구분하고, 쇼룸 행의 표시 값은 {@code showroom}에 담긴다.
 */
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "최근 검색 항목")
public class RecentSearchResponse {

    @Schema(description = "검색 기록 ID (개별 삭제 시 사용)", example = "1")
    private Long id; // Long 타입

    @Schema(description = "행 종류 — TERM: 검색어(탭하면 재검색), SHOWROOM: 쇼룸(탭하면 C4 쇼룸으로)",
            example = "TERM")
    private RecentSearchType type;

    @Schema(description = "검색어. SHOWROOM 행에서는 저장 시점의 쇼룸명 스냅샷이며, 표시는 showroom을 씁니다.",
            example = "브라이")
    private String term;

    @Schema(description = "쇼룸 정보 — SHOWROOM 행에만 채워집니다. TERM 행에서는 null", nullable = true)
    private ShowroomSearchItem showroom;

    @Schema(description = "검색 시각 (UTC 기준)", example = "2026-08-15T10:30:00Z")
    private Instant createdAt;

    /**
     * @param ongoingGroupBuyShowroomIds 진행 중 공구 보유 쇼룸 ID 집합 (아바타 로즈 링 표시용)
     */
    public static RecentSearchResponse from(RecentSearch recentSearch, Set<Long> ongoingGroupBuyShowroomIds) {
        return RecentSearchResponse.builder()
                .id(recentSearch.getId())
                .type(recentSearch.getType())
                .term(recentSearch.getTerm())
                .showroom(toShowroom(recentSearch.getCreator(), ongoingGroupBuyShowroomIds))
                .createdAt(recentSearch.getCreatedAt())
                .build();
    }

    private static ShowroomSearchItem toShowroom(Creator creator, Set<Long> ongoingGroupBuyShowroomIds) {
        if (creator == null) {
            return null;
        }

        return ShowroomSearchItem.builder()
                .showroomId(creator.getId())
                .showroomName(creator.getShowroomName())
                .showroomAddress(creator.getShowroomAddress())
                .showroomImageUrl(creator.getProfileImageUrl())
                .hasOngoingGroupBuy(ongoingGroupBuyShowroomIds.contains(creator.getId()))
                .build();
    }
}
