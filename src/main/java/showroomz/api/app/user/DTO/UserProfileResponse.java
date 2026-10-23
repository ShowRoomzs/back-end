package showroomz.api.app.user.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    @Schema(description = "회원 ID", example = "1")
    private Long id;
    @Schema(description = "이메일 — 소셜 계정이 이메일을 주지 않으면 「{소셜 ID}@social.com」 대체값", example = "user@example.com")
    private String email;
    @Schema(description = "닉네임", example = "수민이네")
    private String nickname;
    @Schema(description = "실명 — 본인인증(PASS) 결과 또는 소셜 계정이 준 이름. 둘 다 없으면 null", example = "홍길동", nullable = true)
    private String name;
    @Schema(description = "프로필 이미지 URL — 없으면 null(기본 이미지)", example = "https://k.kakaocdn.net/img_640x640.jpg", nullable = true)
    private String profileImageUrl;
    @Schema(description = "휴대폰 번호 — 본인인증(PASS) 결과. 인증 전이면 null", example = "010-1234-5678", nullable = true)
    private String phoneNumber;
    @Schema(description = "생년월일 (YYYY-MM-DD) — 본인인증(PASS) 결과. 인증 전이면 null", example = "1990-01-01", nullable = true)
    private String birthday;
    @Schema(description = "성별 — MALE · FEMALE. 본인인증(PASS) 전이면 null", example = "MALE", nullable = true)
    private String gender;
    @Schema(description = "가입 경로 — GOOGLE · FACEBOOK · NAVER · KAKAO · APPLE · LOCAL", example = "GOOGLE")
    private ProviderType providerType;
    @Schema(description = "회원 권한 — USER · SELLER · CREATOR · ADMIN · GUEST(가입 미완료)", example = "USER")
    private RoleType roleType;
    @Schema(description = "가입일시", example = "2025-10-31T10:00:00Z")
    private LocalDateTime createdAt;
    @Schema(description = "회원 정보 수정일시", example = "2025-10-31T10:00:00Z")
    private LocalDateTime modifiedAt;
    @Schema(description = "광고성 정보 수신 동의 여부 — 설정(C15) 알림 토글과 같은 값", example = "true")
    private Boolean marketingAgree;
    // 팔로잉 카운트
    @Schema(description = "내가 팔로우한 쇼룸 수", example = "0")
    private Long followingCount = 0L; // 내가 팔로우한 쇼룸 수

    // 추가된 더미 데이터 필드
    @Schema(description = "보유 쿠폰 수 — 더미 값(현재 항상 3)", example = "3")
    private Long couponCount;
    @Schema(description = "보유 포인트 — 더미 값(현재 항상 5000)", example = "5000")
    private Long point;
    @Schema(description = "작성한 리뷰 수 — 더미 값(현재 항상 12)", example = "12")
    private Long reviewCount;
}

