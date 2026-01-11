package showroomz.api.app.recentSearch.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.recentSearch.entitiy.RecentSearch;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class RecentSearchResponse {

    private Long id; // Long 타입
    private String term;
    private Instant createdAt;

    public static RecentSearchResponse from(RecentSearch recentSearch) {
        return RecentSearchResponse.builder()
                .id(recentSearch.getId())
                .term(recentSearch.getTerm())
                .createdAt(recentSearch.getCreatedAt())
                .build();
    }
}
