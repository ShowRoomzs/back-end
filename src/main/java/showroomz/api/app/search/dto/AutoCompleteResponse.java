package showroomz.api.app.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "검색어 자동완성 후보")
public class AutoCompleteResponse {

    @Schema(description = "상품 후보 — 이름에 키워드가 포함된 전시 중 상품, 이름 짧은 순 최대 5건. 키워드가 없거나 공백이면 빈 배열")
    private List<SearchDto> products;
    @Schema(description = "쇼룸 후보 — 쇼룸명 또는 아이디(@handle)에 키워드가 포함된 공개 쇼룸, 이름 짧은 순 최대 3건. 키워드가 없거나 공백이면 빈 배열")
    private List<SearchDto> showrooms;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "자동완성 후보 항목")
    public static class SearchDto {
        @Schema(description = "후보 ID — products에서는 상품 ID, showrooms에서는 쇼룸(크리에이터) ID", example = "1")
        private Long id;
        @Schema(description = "표시 이름 — 상품명 또는 쇼룸명", example = "화이트 린넨 셔츠")
        private String name;
    }
}
