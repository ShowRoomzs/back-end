package showroomz.api.app.recentSearch.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RecentSearchSyncRequest {
    private List<String> keywords;
}
