package showroomz.api.admin.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserAdminActorType;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.type.UserSuspensionReason;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

public class AdminUserDto {

    // ------------------------------------------------------------------ 정지 · 해제(51-6 설계서 2절)

    @Schema(description = "정지 요청(M1) — 「변경할 상태」 셀렉트는 없다(§25-4)")
    public record SuspendRequest(
            @NotNull @Schema(description = "정지 사유 6종", example = "ABUSIVE_LANGUAGE") UserSuspensionReason reason,
            @Size(max = 500) @Schema(description = "상세 사유 — 기타(OTHER)일 때만 필수 · 앱에 노출되지 않는다") String reasonDetail,
            @Schema(description = "정지 종료일(포함 · 오늘 이상) — 다음 날 00:00 자동 해제(1021 기-02). 비우면 기간 없는 정지(수동 해제)",
                    example = "2026-10-29", nullable = true) java.time.LocalDate endsOn) {
    }

    @Schema(description = "정지 해제 요청 — 사유 필수 · 상세 사유란 없음(§25-4)")
    public record ReleaseRequest(
            @Schema(description = "해제 사유 — RESOLVED(소명 확인 완료) · MISTAKE(오처리 정정) · TERM_ENDED(제재 기간 종료)",
                    example = "RESOLVED") UserReleaseReason reason) {
    }

    @Schema(description = "현재 상태 카드 — 정지 상태에서는 사유 · 처리자 · 일시 · 상세 사유 · 소명 문의가 배지 아래 같은 카드에 모인다")
    public record StatusCard(
            @Schema(example = "SUSPENDED") UserStatus status,
            @Schema(description = "열린 정지 — 없으면 null. 사유 기록 없이 정지된 옛 회원도 null") SanctionInfo sanction,
            Actions actions) {
    }

    public record SanctionInfo(
            Long sanctionId,
            UserSuspensionReason reason,
            @Schema(example = "욕설·비방") String reasonLabel,
            @Schema(description = "상세 사유 — 어드민 전용") String reasonDetail,
            LocalDateTime suspendedAt,
            @Schema(example = "김운영") String suspendedByName,
            @Schema(description = "이 정지의 소명 문의(최신순) — 해제 모달의 「소명 문의 번호 · 클릭 이동」") List<AppealInquiry> appealInquiries) {
    }

    public record AppealInquiry(Long inquiryId, boolean answered, LocalDateTime createdAt) {
    }

    @Schema(description = "버튼 노출 — 탈퇴 회원은 둘 다 false(운영자 액션 없음 · §25-5)")
    public record Actions(boolean canSuspend, boolean canRelease) {
    }

    @Schema(description = "정지 · 해제 응답 — 갱신된 상태 카드와 처리 이력 최신 5건")
    public record StatusResult(StatusCard statusCard, HistorySummary histories) {
    }

    // ------------------------------------------------------------------ 메모 · 처리 이력(51-6 설계서 7절)

    public record Memo(Long memoId, String authorName, String content, LocalDateTime createdAt,
                       LocalDateTime modifiedAt,
                       @Schema(description = "작성자 본인 ∧ 24시간 이내 ∧ 탈퇴 아님") boolean editable) {
    }

    public record MemoPage(long total, List<Memo> content) {
    }

    public record MemoRequest(
            @NotNull @Size(max = 300) @Schema(description = "300자 이내", example = "반품 사유 반복 — 기준 미달이라 정지하지 않음, 다음 반품 시 재검토")
            String content) {
    }

    public record HistoryItem(
            @Schema(description = "저장 이력 id — 파생 행(가입 · 탈퇴 · 재가입 · 소명 접수)은 null") Long historyId,
            UserAdminEventType eventType,
            String label,
            String tone,
            String detail,
            UserAdminActorType actorType,
            @Schema(example = "김운영") String actorName,
            @Schema(description = "SANCTION · MEMO · INQUIRY") String refType,
            Long refId,
            LocalDateTime occurredAt) {
    }

    public record HistorySummary(long total, List<HistoryItem> content) {
    }

    public record HistoryPage(long total, int page, int size, List<HistoryItem> content) {
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

        @Schema(description = "현재 상태 카드 — 정지 · 해제 버튼 · 열린 정지 · 소명 문의")
        private StatusCard statusCard;

        @Schema(description = "관리자 메모 최신 3건 + 전체 건수 — 전체는 GET /{userId}/memos")
        private MemoPage memos;

        @Schema(description = "처리 이력 최신 5건 + 전체 건수 — 전체는 GET /{userId}/histories")
        private HistorySummary histories;

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

        public static UserDetailResponse of(
                Users user,
                Long wishlistCount,
                Long followedShowroomCount,
                Long reviewCount,
                Long inquiryCount,
                StatusCard statusCard,
                MemoPage memos,
                HistorySummary histories) {

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
                    .statusCard(statusCard)
                    .memos(memos)
                    .histories(histories)
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
                    .build();
        }
    }
}
