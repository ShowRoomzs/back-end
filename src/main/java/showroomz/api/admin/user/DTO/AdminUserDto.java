package showroomz.api.admin.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminUserDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "유저 목록 검색 조건")
    public static class SearchCondition {
        @Schema(description = "가입 채널 (GOOGLE, NAVER, KAKAO, APPLE)", example = "GOOGLE")
        private ProviderType providerType;

        @Schema(
                description = "활동 상태 (NORMAL: 정상, DORMANT: 휴면, WITHDRAWN: 탈퇴, SUSPENDED: 정지)",
                example = "NORMAL",
                allowableValues = {"NORMAL", "DORMANT", "WITHDRAWN", "SUSPENDED"})
        private UserStatus status;

        @Schema(description = "가입일 조회 시작 날짜 (yyyy-MM-dd)", example = "2024-01-01")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @Schema(description = "가입일 조회 종료 날짜 (yyyy-MM-dd)", example = "2024-12-31")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "유저 상태 변경 요청")
    public static class UserStatusUpdateRequest {
        @Schema(description = "변경할 상태 (NORMAL, SUSPENDED만 가능)", example = "SUSPENDED")
        private UserStatus status;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "유저 목록 응답")
    public static class UserResponse {
        @Schema(description = "유저 ID", example = "1")
        private Long userId;

        @Schema(description = "이메일 (계정)", example = "user@example.com")
        private String email;

        @Schema(description = "닉네임", example = "홍길동")
        private String nickname;

        @Schema(description = "가입 채널", example = "GOOGLE")
        private ProviderType providerType;

        @Schema(description = "가입일", example = "2024-01-01T10:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "최근 접속일", example = "2024-01-15T14:30:00")
        private LocalDateTime lastLoginAt;

        @Schema(description = "활동 상태", example = "NORMAL")
        private UserStatus status;

        @Schema(description = "누적 구매액 (원, 더미)", example = "1234567")
        private BigDecimal totalPurchaseAmount;

        public static UserResponse from(Users user) {
            return UserResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .providerType(user.getProviderType())
                    .createdAt(user.getCreatedAt())
                    .lastLoginAt(user.getLastLoginAt())
                    .status(user.getStatus())
                    .totalPurchaseAmount(new BigDecimal("1234567"))
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상태 변경 이력 응답")
    public static class UserStatusHistoryDto {
        @Schema(description = "변경 후 상태", example = "NORMAL")
        private UserStatus status;

        @Schema(description = "상태 변경 일시", example = "2024-01-01T10:00:00")
        private LocalDateTime changedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "유저 상세 정보 응답")
    public static class UserDetailResponse {
        @Schema(description = "유저 ID", example = "1")
        private Long userId;

        @Schema(description = "닉네임", example = "홍길동")
        private String nickname;

        @Schema(description = "가입 채널", example = "GOOGLE")
        private ProviderType providerType;

        @Schema(description = "활동 상태", example = "NORMAL")
        private UserStatus status;

        @Schema(description = "생년월일", example = "1990-01-01")
        private String birthday;

        @Schema(description = "성별", example = "MALE")
        private String gender;

        @Schema(description = "기본 배송지 (더미값)", example = "서울특별시 강남구 테헤란로")
        private String defaultAddress;

        @Schema(description = "최초 로그인 시간(가입일)", example = "2024-01-01T10:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "마케팅 동의 여부", example = "true")
        private boolean marketingAgree;

        @Schema(description = "프로필 사진 URL", example = "https://example.com/profile.jpg")
        private String profileImageUrl;

        @Schema(description = "관리자 메모 (내부용)", example = "모니터링 대상")
        private String adminMemo;

        @Schema(description = "누적 구매액 (더미값)", example = "1500000")
        private Long totalPurchaseAmount;

        @Schema(description = "이번달 구매액 (더미값)", example = "300000")
        private Long thisMonthPurchaseAmount;

        @Schema(description = "누적 주문수 (더미값)", example = "15")
        private Integer totalOrderCount;

        @Schema(description = "이번달 주문수 (더미값)", example = "3")
        private Integer thisMonthOrderCount;

        @Schema(description = "평균 주문 금액 (더미값)", example = "100000")
        private Long averageOrderAmount;

        @Schema(description = "최근 주문일 (더미값)", example = "2024-05-01T14:30:00")
        private LocalDateTime lastOrderDate;

        @Schema(description = "상품 위시리스트 수", example = "12")
        private Long productWishlistCount;

        @Schema(description = "팔로우 쇼룸 수", example = "5")
        private Long followedShowroomCount;

        @Schema(description = "작성 리뷰 수", example = "7")
        private Long writtenReviewCount;

        @Schema(description = "문의 내역 수", example = "2")
        private Long inquiryCount;

        @Schema(description = "상태 변경 이력")
        private List<UserStatusHistoryDto> statusHistory;

        public static UserDetailResponse of(
                Users user,
                Long wishlistCount,
                Long followedShowroomCount,
                Long reviewCount,
                Long inquiryCount,
                List<UserStatusHistoryDto> statusHistory) {

            Long dummyTotalPurchaseAmount = 1500000L;
            Long dummyThisMonthPurchaseAmount = 300000L;
            Integer dummyTotalOrderCount = 15;
            Integer dummyThisMonthOrderCount = 3;
            Long dummyAverageOrderAmount = dummyTotalOrderCount > 0
                    ? dummyTotalPurchaseAmount / dummyTotalOrderCount
                    : 0L;
            LocalDateTime dummyLastOrderDate = LocalDateTime.now().minusDays(5);

            return UserDetailResponse.builder()
                    .userId(user.getId())
                    .nickname(user.getNickname())
                    .providerType(user.getProviderType())
                    .status(user.getStatus())
                    .birthday(user.getBirthday())
                    .gender(user.getGender())
                    .defaultAddress("서울특별시 강남구 테헤란로 123, 101호")
                    .createdAt(user.getCreatedAt())
                    .marketingAgree(user.isMarketingAgree())
                    .profileImageUrl(user.getProfileImageUrl())
                    .adminMemo(user.getAdminMemo())
                    .totalPurchaseAmount(dummyTotalPurchaseAmount)
                    .thisMonthPurchaseAmount(dummyThisMonthPurchaseAmount)
                    .totalOrderCount(dummyTotalOrderCount)
                    .thisMonthOrderCount(dummyThisMonthOrderCount)
                    .averageOrderAmount(dummyAverageOrderAmount)
                    .lastOrderDate(dummyLastOrderDate)
                    .productWishlistCount(wishlistCount)
                    .followedShowroomCount(followedShowroomCount)
                    .writtenReviewCount(reviewCount)
                    .inquiryCount(inquiryCount)
                    .statusHistory(statusHistory)
                    .build();
        }
    }
}
