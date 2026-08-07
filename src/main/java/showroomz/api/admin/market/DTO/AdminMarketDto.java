package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.market.type.SnsType;
import showroomz.global.dto.PaginationInfo;

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

        @Schema(
                description = "신청서 상태 필터 (PENDING: 심사대기, APPROVED: 승인, REJECTED: 반려, 미입력 시 전체)",
                example = "PENDING",
                allowableValues = {"PENDING", "APPROVED", "REJECTED"}
        )
        private SellerStatus status;

        @Schema(description = "브랜드명(마켓명) 검색어 (부분 일치)", example = "멋쟁이 옷장")
        private String keyword;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상태별 신청서 건수")
    public static class ApplicationStatusCounts {

        @Schema(description = "전체 건수", example = "42")
        private long all;

        @Schema(description = "심사대기 건수", example = "10")
        private long pending;

        @Schema(description = "승인 건수", example = "25")
        private long approved;

        @Schema(description = "반려 건수", example = "7")
        private long rejected;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 가입 신청 목록 응답 (상태별 건수 포함)")
    public static class ApplicationListResponse {

        @Schema(description = "신청 목록")
        private List<ApplicationResponse> content;

        @Schema(description = "페이징 정보")
        private PaginationInfo pageInfo;

        @Schema(description = "상태별 신청서 건수 (검색어 반영, 상태 필터 미반영)")
        private ApplicationStatusCounts statusCounts;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "어드민 마켓 목록 검색 조건")
    public static class MarketSearchRequest {

        @Schema(description = "대표(메인) 카테고리 ID 필터 (통합 검색과 별도, 미입력 시 전체)", example = "1")
        private Long mainCategoryId;

        @Schema(
                description = "검색 타입\n" +
                        "- MARKET_ID: 마켓 ID\n" +
                        "- MARKET_NAME: 마켓명\n" +
                        "- MANAGER_NAME: 담당자명\n" +
                        "- CONTACT: 연락처(전화번호)",
                example = "MARKET_NAME",
                allowableValues = {"MARKET_ID", "MARKET_NAME", "MANAGER_NAME", "CONTACT"}
        )
        private String keywordType;

        @Schema(description = "검색어 (부분 일치, keywordType과 함께 사용)", example = "멋쟁이")
        private String keyword;

        @Schema(
                description = "마켓 운영 상태 필터 (ACTIVE, SUSPENDED, DORMANT, WITHDRAWN, 미입력 시 전체)",
                example = "ACTIVE"
        )
        private MarketStatus status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 운영 상태 변경 요청")
    public static class UpdateMarketStatusRequest {

        @NotNull(message = "변경할 마켓 상태를 입력해주세요.")
        @Schema(description = "마켓 상태 (ACTIVE: 활성, SUSPENDED: 정지)", example = "ACTIVE", allowableValues = {"ACTIVE", "SUSPENDED"})
        private MarketStatus status;
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

        @Schema(description = "누적 판매액 (미구현, 현재 0 고정)", example = "0")
        private Long totalSalesAmount;

        @Schema(description = "마켓 운영 상태 (ACTIVE, SUSPENDED, DORMANT, WITHDRAWN)", example = "ACTIVE")
        private MarketStatus marketStatus;

        @Schema(description = "관리자 처리 일시 (승인/반려 등, 미처리 시 null)", example = "2024-01-01T10:00:00")
        private LocalDateTime processedDate;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 가입 신청 정보 응답")
    public static class ApplicationResponse {
        @Schema(description = "입점 신청서 ID", example = "100")
        private Long applicationId;

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

        @Schema(description = "등록 상품 수", example = "48")
        private Long registeredProductCount;

        @Schema(description = "누적 판매액(원, 더미)", example = "12450000")
        private Long totalSalesAmount;

        @Schema(description = "이번 달 누적 판매액(원, 더미)", example = "1230000")
        private Long monthlySalesAmount;

        @Schema(description = "누적 주문 수(더미)", example = "842")
        private Long totalOrderCount;

        @Schema(description = "이번 달 누적 주문 수(더미)", example = "67")
        private Long monthlyOrderCount;

        @Schema(description = "입점일(판매자 승인 처리 일시, 미승인 시 null)", example = "2024-01-02T15:30:00")
        private LocalDateTime processedDate;

        @Schema(description = "운영 기간(입점일 기준 경과 개월 수, 입점일 없으면 0)", example = "14")
        private int operatingMonths;

        @Schema(description = "현재 마켓 운영 상태 (ACTIVE, SUSPENDED)", example = "ACTIVE")
        private MarketStatus marketStatus;

        @Schema(description = "관리자 메모")
        private String adminMemo;

        @Schema(description = "셀러 가입일", example = "2024-01-01T12:00:00")
        private LocalDateTime joinedAt;

        @Schema(description = "최근 정산일(더미)", example = "2026-04-25")
        private LocalDate lastSettlementDate;

        @Schema(description = "미정산액(원, 더미)", example = "340000")
        private Long unsettledAmount;

        @Schema(description = "최근 로그인 일시", example = "2026-05-02T09:15:00")
        private LocalDateTime lastLoginAt;

        @Schema(description = "사업자 구분", example = "법인사업자")
        private String businessType;

        @Schema(description = "대표자명", example = "홍길동")
        private String representativeName;

        @Schema(description = "대표자 연락처", example = "010-1111-2222")
        private String representativeContact;

        @Schema(description = "사업자등록증 상호명", example = "(주)멋쟁이")
        private String companyName;

        @Schema(description = "사업자 등록번호", example = "123-45-67890")
        private String businessRegistrationNumber;

        @Schema(description = "업태", example = "도소매")
        private String businessCondition;

        @Schema(description = "사업장 주소", example = "서울특별시 강남구 테헤란로 123")
        private String businessAddress;

        @Schema(description = "상세 주소", example = "OO빌딩 5층")
        private String detailAddress;

        @Schema(description = "세금계산서용 이메일", example = "tax@example.com")
        private String taxEmail;

        @Schema(description = "사업자등록증 사본 URL")
        private String businessLicenseImageUrl;

        @Schema(description = "통신판매업 신고증 사본 URL")
        private String mailOrderRegImageUrl;

        @Schema(description = "통신판매업 신고번호", example = "2024-서울강남-01234")
        private String mailOrderRegNumber;

        @Schema(description = "은행명 (가입 시 bankCode로 조회한 은행명)", example = "KB국민은행")
        private String bankName;

        @Schema(description = "예금주명", example = "홍길동")
        private String accountHolder;

        @Schema(description = "계좌번호", example = "123456-78-901234")
        private String accountNumber;

        @Schema(description = "통장 사본 URL")
        private String bankbookImageUrl;
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
