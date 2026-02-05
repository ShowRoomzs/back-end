package showroomz.api.app.recentSearch.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RecentSearchSyncRequest {

    private List<RecentSearchSyncItem> keywords;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RecentSearchSyncItem {
        private String keyword;
        private Instant createdAt;
    }
}
