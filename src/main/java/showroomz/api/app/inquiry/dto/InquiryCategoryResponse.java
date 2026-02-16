package showroomz.api.app.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class InquiryCategoryResponse {

    private String key;           // 대분류 코드 (예: DELIVERY)
    private String description;   // 대분류 명 (예: 배송)
    private List<DetailResponse> details; // 소분류 목록

    @Getter
    @AllArgsConstructor
    public static class DetailResponse {
        private String key;           // 소분류 코드 (예: RESERVED_DELIVERY)
        private String description;   // 소분류 명 (예: 예약 배송)
    }
}
