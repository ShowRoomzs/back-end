package showroomz.api.admin.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

public class AdminUserDto {

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
    @Schema(description = "소비자 목록 행 (§25-3 컬럼 8종)")
    public static class ListItem {
        @Schema(description = "회원 ID — 행 클릭 시 상세 경로에 쓴다", example = "88231")
        private Long userId;

        @Schema(description = "회원번호 — 첫 열. CS에서 문의받은 번호를 눈으로 대조한다", example = "CST-88231")
        private String memberNo;

        @Schema(description = "닉네임", example = "홍길동")
        private String nickname;

        @Schema(
                description = "이름 — 가운데 1자 마스킹. 목록에는 해제 경로가 없어 원본을 내려보내지 않는다",
                example = "홍*동"
        )
        private String maskedName;

        @Schema(
                description = "휴대폰 — 가운데 4자리 마스킹. 뒤 4자리가 남아 검색·대조가 된다",
                example = "010-****-1234"
        )
        private String maskedPhone;

        @Schema(description = "가입 수단 — 상태가 아니라 속성이라 화면은 배지가 아닌 색점+이름으로 그린다", example = "KAKAO")
        private ProviderType providerType;

        @Schema(description = "가입일", example = "2026-02-01T10:00:00")
        private LocalDateTime joinedAt;

        @Schema(description = "누적 주문 건수 — 취소만 남은 주문은 세지 않는다. 0건은 화면에서 회색으로 강등", example = "14")
        private long orderCount;

        @Schema(description = "상태 — 활성(성공)/정지(위험)/탈퇴(중립)", example = "NORMAL")
        private UserStatus status;
    }

    @Getter
    @Builder
    @Schema(description = "목록 요약 — 상태 조건만 제외하고 검색어·가입 수단은 그대로 반영한 건수")
    public static class ListSummary {
        @Schema(description = "전체", example = "2340")
        private long total;

        @Schema(description = "활성", example = "2268")
        private long active;

        @Schema(description = "정지", example = "12")
        private long suspended;

        @Schema(description = "탈퇴", example = "60")
        private long withdrawn;

        @Schema(
                description = "최근 30일 신규 정지 — 정지 탭에서만 내려온다. 다른 탭에서는 null이라 화면이 행을 그리지 않는다",
                example = "3"
        )
        private Long newSuspendedIn30Days;
    }

    @Getter
    @Builder
    @Schema(description = "소비자 목록 응답")
    public static class ListResponse {
        @Schema(description = "목록 행")
        private List<ListItem> content;

        @Schema(description = "페이지 정보")
        private PaginationInfo pageInfo;

        @Schema(description = "탭 건수 및 요약 줄")
        private ListSummary summary;
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
