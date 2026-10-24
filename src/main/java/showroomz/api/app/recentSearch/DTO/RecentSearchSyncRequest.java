package showroomz.api.app.recentSearch.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "로컬 검색어 동기화 요청 (로그인 직후)")
public class RecentSearchSyncRequest {

    @Schema(description = "동기화할 로컬 검색어 목록 — 비어 있거나 null이면 아무것도 저장하지 않는다")
    private List<RecentSearchSyncItem> keywords;

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "동기화할 검색어 한 건")
    public static class RecentSearchSyncItem {
        @Schema(description = "검색어 — 빈 문자열 · null 항목은 무시. 이미 있는 검색어면 검색 시각만 갱신", example = "화이트")
        private String keyword;
        @Schema(description = "검색 시각 (UTC · 선택) — 없으면 서버 현재 시각", example = "2026-02-05T10:15:40.673Z")
        private Instant createdAt;
    }
}
