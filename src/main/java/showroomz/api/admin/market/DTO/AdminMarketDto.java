package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.type.SnsType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminMarketDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 가입 신청 목록 검색 조건")
    public static class SearchCondition {
        
        @Schema(description = "판매자 상태 (NULL 또는 비워둘 시 전체 조회)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "조회 시작일 (YYYY-MM-DD)", example = "2024-01-01")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @Schema(description = "조회 종료일 (YYYY-MM-DD)", example = "2024-12-31")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @Schema(description = "검색어 (부분 일치 검색, keywordType과 함께 사용)", example = "홍길동")
        private String keyword;

        @Schema(
                description = "검색 타입\n" +
                        "- SELLER_ID: 신청 ID로 검색\n" +
                        "- MARKET_NAME: 마켓명으로 검색\n" +
                        "- NAME: 담당자 이름으로 검색\n" +
                        "- PHONE_NUMBER: 연락처로 검색\n" +
                        "- BUSINESS_NUMBER: 사업자 등록번호로 검색",
                example = "NAME",
                allowableValues = {"SELLER_ID", "MARKET_NAME", "NAME", "PHONE_NUMBER", "BUSINESS_NUMBER"}
        )
        private KeywordType keywordType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "어드민 마켓 목록 검색 조건")
    public static class MarketListSearchCondition {

        @Schema(description = "대표 카테고리 ID 필터", example = "1")
        private Long mainCategoryId;

        @Schema(description = "마켓명 검색어", example = "멋쟁이")
        private String marketName;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "어드민 마켓 목록 응답 (상품 수 포함)")
    public static class MarketResponse {

        @Schema(description = "마켓 ID", example = "10")
        private Long marketId;

        @Schema(description = "마켓명", example = "멋쟁이 옷장")
        private String marketName;

        @Schema(description = "대표 카테고리 ID", example = "1")
        private Long mainCategoryId;

        @Schema(description = "대표 카테고리명", example = "의류")
        private String mainCategoryName;

        @Schema(description = "판매자명 (담당자)", example = "홍길동")
        private String sellerName;

        @Schema(description = "연락처", example = "010-1234-5678")
        private String phoneNumber;

        @Schema(description = "등록 상품 수", example = "120")
        private Long productCount;

        @Schema(description = "입점일", example = "2024-01-01T10:00:00")
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 가입 신청 정보 응답")
    public static class ApplicationResponse {
        @Schema(description = "판매자 ID", example = "1")
        private Long sellerId;

        @Schema(description = "마켓 ID", example = "10")
        private Long marketId;

        @Schema(description = "이메일", example = "seller@example.com")
        private String email;

        @Schema(description = "판매자명 (대표자명)", example = "홍길동")
        private String name;

        @Schema(description = "마켓명", example = "멋쟁이 옷장")
        private String marketName;

        @Schema(description = "연락처", example = "010-1234-5678")
        private String phoneNumber;

        @Schema(description = "상태 (PENDING, APPROVED, REJECTED)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "거부 사유 (반려 시)", example = "서류 미비")
        private String rejectionReason;

        @Schema(description = "가입 신청일", example = "2024-01-01T12:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "사업자 구분", example = "법인사업자")
        private String businessType;

        @Schema(description = "사업자 등록번호", example = "123-45-67890")
        private String businessNumber;

        @Schema(description = "신청 처리 일시", example = "2024-01-02T15:30:00")
        private LocalDateTime processedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "어드민 마켓 상세/수정용 응답")
    public static class MarketAdminDetailResponse {
        @Schema(description = "마켓 ID", example = "10")
        private Long marketId;

        @Schema(description = "마켓명", example = "멋쟁이 옷장")
        private String marketName;

        @Schema(description = "고객센터 번호", example = "02-1234-5678")
        private String csNumber;

        @Schema(description = "마켓 이미지 URL", example = "https://example.com/image.jpg")
        private String marketImageUrl;

        @Schema(description = "마켓 소개글", example = "멋진 옷을 판매하는 마켓입니다.")
        private String marketDescription;

        @Schema(description = "마켓 URL", example = "https://www.showroomz.co.kr/market/10")
        private String marketUrl;

        @Schema(description = "대표 카테고리 ID", example = "1")
        private Long mainCategoryId;

        @Schema(description = "대표 카테고리명", example = "의류")
        private String mainCategoryName;

        @Schema(description = "SNS 링크 목록")
        private List<SnsLinkResponse> snsLinks;

        // Entity -> DTO 변환 편의 메서드
        public static MarketAdminDetailResponse from(Market market) {
            // SNS 링크 변환
            List<SnsLinkResponse> links = market.getSnsLinks().stream()
                    .map(sns -> new SnsLinkResponse(sns.getSnsType().name(), sns.getSnsUrl()))
                    .collect(java.util.stream.Collectors.toList());

            return MarketAdminDetailResponse.builder()
                    .marketId(market.getId())
                    .marketName(market.getMarketName())
                    .csNumber(market.getCsNumber())
                    .marketImageUrl(market.getMarketImageUrl())
                    .marketDescription(market.getMarketDescription())
                    .marketUrl(market.getMarketUrl())
                    .mainCategoryId(market.getMainCategory() != null ? market.getMainCategory().getCategoryId() : null)
                    .mainCategoryName(market.getMainCategory() != null ? market.getMainCategory().getName() : null)
                    .snsLinks(links)
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor
    @Schema(description = "SNS 링크 정보")
    public static class SnsLinkResponse {
        @Schema(
                description = "SNS 타입 (INSTAGRAM, TIKTOK, X, YOUTUBE)",
                example = "INSTAGRAM",
                allowableValues = {"INSTAGRAM", "TIKTOK", "X", "YOUTUBE"}
        )
        private String snsType;
        
        @Schema(description = "URL", example = "https://instagram.com/example")
        private String snsUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "크리에이터 가입 신청 목록 응답")
    public static class CreatorApplicationResponse {
        @Schema(description = "가입 신청 PK (크리에이터 ID)", example = "5")
        private Long creatorId;

        @Schema(description = "쇼룸명 (크리에이터명)", example = "감성 룩북")
        private String showroomName;

        @Schema(description = "신청일", example = "2024-03-01T14:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "이름 (본명)", example = "김지수")
        private String name;

        @Schema(description = "전화번호", example = "010-1111-2222")
        private String phoneNumber;

        @Schema(description = "상태 (PENDING, APPROVED, REJECTED)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "거부 사유 (반려 시)", example = "플랫폼 확인 불가")
        private String rejectionReason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "크리에이터 가입 신청 상세 응답")
    public static class CreatorDetailResponse {
        @Schema(description = "가입 신청 PK (크리에이터 ID)", example = "5")
        private Long creatorId;

        @Schema(description = "이메일", example = "creator@example.com")
        private String email;

        @Schema(description = "쇼룸명", example = "감성 룩북")
        private String showroomName;

        @Schema(description = "활동명 (크리에이터 활동 채널명)", example = "감성크리에이터지수")
        private String activityName;

        @Schema(description = "플랫폼 유형", allowableValues = {"INSTAGRAM", "TIKTOK", "X", "YOUTUBE"}, example = "INSTAGRAM")
        private SnsType platformType;

        @Schema(description = "플랫폼 URL", example = "https://instagram.com/creator_jisu")
        private String platformUrl;

        @Schema(description = "이름 (본명)", example = "김지수")
        private String name;

        @Schema(description = "전화번호", example = "010-1111-2222")
        private String phoneNumber;

        @Schema(description = "상태 (PENDING, APPROVED, REJECTED)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "거부 사유 (반려 시)", example = "플랫폼 확인 불가")
        private String rejectionReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "크리에이터 가입 신청 목록 검색 조건")
    public static class CreatorSearchCondition {

        @Schema(description = "크리에이터 상태 (NULL 또는 비워둘 시 전체 조회)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "조회 시작일 (YYYY-MM-DD)", example = "2024-01-01")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @Schema(description = "조회 종료일 (YYYY-MM-DD)", example = "2024-12-31")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        @Schema(description = "검색어 (부분 일치 검색, keywordType과 함께 사용)", example = "김지수")
        private String keyword;

        @Schema(
                description = "검색 타입\n" +
                        "- CREATOR_ID: 크리에이터 ID로 검색\n" +
                        "- SHOWROOM_NAME: 크리에이터명(쇼룸명)으로 검색\n" +
                        "- NAME: 이름(본명)으로 검색\n" +
                        "- PHONE_NUMBER: 전화번호로 검색",
                example = "NAME",
                allowableValues = {"CREATOR_ID", "SHOWROOM_NAME", "NAME", "PHONE_NUMBER"}
        )
        private CreatorKeywordType keywordType;
    }

    /**
     * 마켓 검색 타입 Enum
     */
    @Getter
    @AllArgsConstructor
    @Schema(description = "마켓 키워드 검색 타입")
    public enum KeywordType {
        SELLER_ID("신청 ID"),
        MARKET_NAME("마켓명"),
        NAME("담당자 이름"),
        PHONE_NUMBER("연락처"),
        BUSINESS_NUMBER("사업자 등록번호");

        private final String description;
    }

    /**
     * 크리에이터 검색 타입 Enum
     */
    @Getter
    @AllArgsConstructor
    @Schema(description = "크리에이터 키워드 검색 타입")
    public enum CreatorKeywordType {
        CREATOR_ID("크리에이터 ID"),
        SHOWROOM_NAME("크리에이터명(쇼룸명)"),
        NAME("이름(본명)"),
        PHONE_NUMBER("전화번호");

        private final String description;

        /** Repository 쿼리에서 사용하는 keywordType 문자열로 변환 */
        public String toQueryType() {
            return switch (this) {
                case CREATOR_ID   -> "SELLER_ID";
                case SHOWROOM_NAME -> "MARKET_NAME";
                case NAME          -> "NAME";
                case PHONE_NUMBER  -> "PHONE_NUMBER";
            };
        }
    }
}
