package showroomz.api.app.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 검색어 자동완성 후보.
 *
 * <p>{@code markets}가 사라졌다 — 소비자 앱에서 마켓(브랜드)은 조회되지 않는다. 팔아 주는 얼굴은
 * 쇼룸이고, 마켓은 그 뒤에서 상품을 대는 쪽이라 소비자가 이름으로 찾아갈 대상이 아니다.
 */
@Getter
@Builder
public class AutoCompleteResponse {

    private List<SearchDto> products;
    private List<SearchDto> showrooms;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchDto {
        private Long id;
        private String name;
    }
}
