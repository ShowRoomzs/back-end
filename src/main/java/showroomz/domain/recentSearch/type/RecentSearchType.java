package showroomz.domain.recentSearch.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C14 최근 검색 행의 종류.
 * 두 종류가 한 목록에 시간순으로 섞이며, 탭했을 때의 행동이 서로 다르다.
 */
@Getter
@AllArgsConstructor
public enum RecentSearchType {

    /** 검색어 — 탭하면 그 단어로 재검색한다. */
    TERM("검색어"),

    /** 쇼룸 — 탭하면 검색을 거치지 않고 바로 해당 쇼룸으로 간다. */
    SHOWROOM("쇼룸");

    private final String description;
}
