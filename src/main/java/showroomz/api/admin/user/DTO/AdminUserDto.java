package showroomz.api.admin.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdminUserDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "유저 목록 검색 조건")
    public static class SearchCondition {
        @Schema(description = "가입 채널 (GOOGLE, NAVER, KAKAO, APPLE)", example = "GOOGLE")
        private ProviderType providerType;

        @Schema(description = "활동 상태 (NORMAL, DORMANT, WITHDRAWN)", example = "NORMAL")
        private UserStatus status;

        @Schema(description = "가입일 조회 시작 날짜 (yyyy-MM-dd)", example = "2024-01-01")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @Schema(description = "가입일 조회 종료 날짜 (yyyy-MM-dd)", example = "2024-12-31")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;
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

        public static UserResponse from(Users user) {
            return UserResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .providerType(user.getProviderType())
                    .createdAt(user.getCreatedAt())
                    .lastLoginAt(user.getLastLoginAt())
                    .status(user.getStatus())
                    .build();
        }
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

        @Schema(description = "전화번호 (더미값)", example = "010-0000-0000")
        private String phoneNumber;

        @Schema(description = "기본 배송지 (더미값)", example = "서울특별시 강남구 테헤란로")
        private String defaultAddress;

        @Schema(description = "최초 로그인 시간(가입일)", example = "2024-01-01T10:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "마케팅 동의 여부", example = "true")
        private boolean marketingAgree;

        @Schema(description = "프로필 사진 URL", example = "https://example.com/profile.jpg")
        private String profileImageUrl;

        public static UserDetailResponse from(Users user) {
            return UserDetailResponse.builder()
                    .userId(user.getId())
                    .nickname(user.getNickname())
                    .providerType(user.getProviderType())
                    .status(user.getStatus())
                    .birthday(user.getBirthday())
                    .gender(user.getGender())
                    // 요청하신 대로 더미 값으로 설정 (DB에 값이 있어도 더미 반환)
                    .phoneNumber("010-0000-0000")
                    .defaultAddress("서울특별시 강남구 테헤란로 123, 101호")
                    .createdAt(user.getCreatedAt())
                    .marketingAgree(user.isMarketingAgree())
                    .profileImageUrl(user.getProfileImageUrl())
                    .build();
        }
    }
}
