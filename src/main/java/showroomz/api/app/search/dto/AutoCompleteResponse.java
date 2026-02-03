package showroomz.api.app.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
public class AutoCompleteResponse {

    private List<SearchDto> products;
    private List<SearchDto> markets;
    private List<SearchDto> showrooms;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchDto {
        private Long id;
        private String name;
    }
}
