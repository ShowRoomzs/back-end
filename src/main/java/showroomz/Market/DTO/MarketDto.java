package showroomz.Market.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

public class MarketDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckMarketNameResponse {
        private boolean isAvailable;
        private String code;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MarketProfileResponse {
        private Long marketId;
        private String marketName;
        private String csNumber;
        private String marketImageUrl;
        // 검수 상태 반환
        private String marketImageStatus; // "APPROVED", "UNDER_REVIEW", "REJECTED"
        private String marketDescription;
        private String marketUrl;
        private String mainCategory;
        private List<SnsLinkRequest> snsLinks; // 프론트엔드에 리스트 형태로 반환
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnsLinkRequest {
        private String snsType; // SNS 종류 (INSTAGRAM, YOUTUBE 등)
        
        // URL 형식 검증 (간단한 예시, 필요 시 정교한 정규식 사용)
        @Pattern(regexp = "^(http|https)://.*$", message = "올바른 URL 형식이 아닙니다.")
        private String snsUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMarketProfileRequest {
        
        // 마켓명: 한글만 허용, 공백 불가
        @Pattern(regexp = "^[가-힣]+$", message = "마켓명은 한글만 입력 가능하며 공백을 포함할 수 없습니다.")
        private String marketName;

        // 마켓 소개: 최대 30자, 줄바꿈 불가(컨트롤러/서비스 단에서 추가 검증 가능)
        @Size(max = 30, message = "마켓 소개는 최대 30자까지 입력 가능합니다.")
        private String marketDescription;

        private String marketImageUrl;

        private String mainCategory;

        // SNS 링크: 최대 3개
        @Size(max = 3, message = "SNS 링크는 최대 3개까지 등록 가능합니다.")
        @Valid // 중첩된 객체의 validation을 활성화
        private List<SnsLinkRequest> snsLinks;
    }

    // 검수 상태 변경 요청 DTO
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateImageStatusRequest {
        private String status; // APPROVED, REJECTED
    }
}

