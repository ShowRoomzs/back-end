package showroomz.api.admin.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import showroomz.api.admin.common.AdminMemberNavigation;
import showroomz.api.admin.common.AdminReasonOption;
import showroomz.api.admin.common.AdminSuspensionTermPreview;
import showroomz.api.admin.transaction.dto.AdminOrderDto;
import showroomz.api.admin.user.service.AdminUserDetailAssembler;
import showroomz.api.admin.user.service.AdminUserTradeReader;
import showroomz.api.admin.user.type.AdminUserInquiryKind;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserAdminActorType;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.NotificationToggle;
import showroomz.domain.member.user.type.UserPendingChip;
import showroomz.domain.member.user.type.UserPrivacyAccessReason;
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
            @Schema(description = "탈퇴 정보 — 탈퇴(WITHDRAWN) 계정만 · 그 밖에는 null(56 C3 · 브랜드 · 인플루언서 상세의 withdrawal 과 같은 자리)",
                    nullable = true) Withdrawal withdrawal,
            Actions actions) {
    }

    @Schema(name = "AdminUserWithdrawal",
            description = "탈퇴 블록 「탈퇴 일시」 — 소비자 탈퇴는 앱 셀프 탈퇴뿐이라 신청 · 승인 시각 · 처리자가 따로 없다(「탈퇴 경로 · 보존 · 파기 항목」은 고정 문구)")
    public record Withdrawal(
            @Schema(description = "탈퇴 일시 — 가장 최근의 탈퇴 전이(user_status_history). 처리 이력 「회원 탈퇴」 행과 같은 시각. "
                    + "전이 기록이 없는 옛 탈퇴 회원은 null", example = "2026-07-19T20:02:00", nullable = true)
            LocalDateTime withdrawnAt) {
    }

    public record SanctionInfo(
            Long sanctionId,
            UserSuspensionReason reason,
            @Schema(example = "욕설·비방") String reasonLabel,
            @Schema(description = "상세 사유 — 어드민 전용") String reasonDetail,
            LocalDateTime suspendedAt,
            @Schema(example = "김운영") String suspendedByName,
            @Schema(description = "이 정지의 소명 문의(최신순) — 해제 모달의 「소명 문의 번호 · 클릭 이동」") List<AppealInquiry> appealInquiries,
            @Schema(description = "정지 기간 · 자동 해제 · 이의 신청 기한 · 소명 검토 일수(1021 기-02) — 파트너 · 스튜디오 · 앱 배너와 같은 꼴 · 55 계획서 M5")
            showroomz.api.common.account.dto.MemberAccountStatusResponse.Term term) {
    }

    public record AppealInquiry(
            Long inquiryId,
            @Schema(description = "문의번호 — 1:1 문의 관리 · 최근 문의와 같은 INQ-YYYYMMDD-NNN(접수일 내 순번이라 FE 가 만들 수 없다 · 56 C4). "
                    + "해제 모달 「소명 문의 INQ-20260810-027 · 답변완료」", example = "INQ-20260810-027") String inquiryNumber,
            @Schema(description = "답변 완료 여부 — 「답변완료」 · 「답변 대기」") boolean answered,
            LocalDateTime createdAt) {
    }

    @Schema(description = "버튼 노출 — 탈퇴 회원은 둘 다 false(운영자 액션 없음 · §25-5)")
    public record Actions(boolean canSuspend, boolean canRelease) {
    }

    @Schema(description = "정지 · 해제 응답 — 갱신된 상태 카드와 처리 이력 최신 5건")
    public record StatusResult(StatusCard statusCard, HistorySummary histories) {
    }

    @Schema(name = "AdminUserReasonOptions",
            description = "모달 셀렉트 사유 목록 — 정지(M1) · 해제(M4) · 마스킹 해제(M2). 브랜드 · 인플루언서 상세와 같은 행 모양(AdminReasonOption) · 55 계획서 M8")
    public record ReasonOptions(
            @Schema(description = "정지 사유 6종 — 소비자 정지는 지급과 무관해 holdsPayout 은 null") List<AdminReasonOption> suspensionReasons,
            @Schema(description = "해제 사유 3종 — TERM_ENDED(제재 기간 종료)를 운영자도 고른다(D18 · 브랜드 · 인플루언서에는 없다)")
            List<AdminReasonOption> releaseReasons,
            @Schema(description = "마스킹 해제 사유 4종 — 정형 사유만 · 기타 · 상세 없음(M3-3)") List<AdminReasonOption> privacyUnmaskReasons,
            @Schema(description = "정지 모달(M1) 미리보기 — 이의 신청 기한 영업일 · 지금 정지하면 잡힐 기한 · 소명 검토 영업일(브랜드 · 인플루언서와 같은 모양)")
            AdminSuspensionTermPreview suspensionTerm) {
    }

    @Schema(name = "AdminUserUnmaskRequest", description = "마스킹 해제 요청(M2) — 정형 사유 4종 중 하나 · 상세 사유란 없음")
    public record UnmaskRequest(@NotNull @Schema(example = "CS_RESPONSE") UserPrivacyAccessReason reason) {
    }

    @Schema(name = "AdminUserUnmask",
            description = "마스킹 해제 상태 — 본인확인 카드 헤더 「해제됨 · 처리자 · 시각 · mm:ss 후 잠금」 + [지금 잠금]. 세션은 이 소비자 1건 · 이 운영자 · 10분 고정 만료")
    public record Unmask(
            @Schema(description = "해제 버튼 활성 — 최고관리자 한정(등급 모델이 없어 지금은 ADMIN 이면 통과) · 탈퇴(파기) 회원은 false") boolean allowed,
            @Schema(description = "이 운영자의 유효한 세션이 있어 실값으로 열렸는가") boolean unmasked,
            @Schema(example = "김운영", nullable = true) String operatorName,
            @Schema(nullable = true) LocalDateTime unmaskedAt,
            @Schema(nullable = true) LocalDateTime expiresAt,
            @Schema(description = "자동 잠금까지 남은 초", example = "581", nullable = true) Long remainingSeconds,
            @Schema(description = "직전 해제 사유 — 무조작 10분 만료 뒤 재해제 프리필(시안 A3) · 운영자 무관 최신", nullable = true)
            UserPrivacyAccessReason lastReason) {
    }

    // ------------------------------------------------------------------ 상세 보강(55 계획서 M3-2 · ui-admin-13 rev.7)

    @Schema(name = "AdminUserNotificationStatus",
            description = "알림 수신 — 「알림을 못 받았다」 문의에 답할 근거(CS 응대 보강 ③). 탈퇴는 파기라 destroyed = true · 값은 null(「— 파기됨」)")
    public record NotificationStatus(
            boolean destroyed,
            @Schema(description = "앱 푸시 켬 = 기기 토큰 있음 ∧ 아래 토글 중 하나라도 켬(D15). false 면 경고색", nullable = true) Boolean appPushOn,
            @Schema(description = "푸시를 받을 기기 토큰이 있는가 — 앱에서 알림 권한을 허용하면 등록된다", nullable = true) Boolean deviceRegistered,
            @Schema(description = "C15 알림 설정 토글 4개(화면 순서) — 모두 광고성이다. 주문 · 배송 · 문의 답변 알림은 끌 수 없어 없다") List<NotificationToggleState> toggles,
            @Schema(description = "광고성 정보 수신 동의(가입 [선택] · 「혜택 · 이벤트」 토글과 같은 값)", nullable = true) Boolean marketingAgree,
            @Schema(description = "광고성 동의 변경 시각 — 켜져 있으면 동의 일시(「광고성 수신 동의 2026.02.01 10:23」), 꺼져 있으면 철회 일시",
                    nullable = true) LocalDateTime marketingAgreeChangedAt) {
    }

    @Schema(name = "AdminUserNotificationToggle")
    public record NotificationToggleState(NotificationToggle toggle, @Schema(example = "팔로우 쇼룸 새 공구") String label, boolean on,
                                          @Schema(description = "켠 시각 — 꺼져 있으면 null", nullable = true) LocalDateTime agreedAt) {
    }

    @Schema(name = "AdminUserIdentity",
            description = "본인확인 정보 — 기본 마스킹(서버가 끝낸다). 탈퇴는 파기라 destroyed = true · 값은 전부 null. 「— 파기됨」은 값이 있었다가 지워졌다는 뜻이라 "
                    + "애초에 수집하지 않은 항목(내외국인 · 통신사 · 갱신 전 최초 인증 시각)은 null 이어도 파기 표시를 하지 않는다")
    public record Identity(
            @Schema(description = "탈퇴 파기") boolean destroyed,
            @Schema(description = "실값으로 열렸는가 — 이 운영자의 유효한 열람 세션이 있을 때만") boolean unmasked,
            @Schema(example = "홍*동", nullable = true) String name,
            @Schema(description = "마스킹 중 연도만 · 해제 시 전체", example = "1990.**.**", nullable = true) String birthday,
            @Schema(description = "만 나이 — 해제 상태에서만", example = "36", nullable = true) Integer age,
            @Schema(description = "MALE · FEMALE — 해제 대상이 아니다(시안 A1 마스킹 상태에서도 보인다)", example = "FEMALE", nullable = true) String gender,
            @Schema(example = "여성", nullable = true) String genderLabel,
            @Schema(description = "내외국인 — PASS 미연동이라 수집하지 않는다(항상 null · 인플루언서와 같다)", nullable = true) String nationality,
            @Schema(example = "010-****-1234", nullable = true) String phone,
            @Schema(description = "통신사 — 수집하지 않는다(항상 null)", nullable = true) String carrier,
            @Schema(description = "최초 인증 — 재인증한 적이 없을 때만(재인증은 시각을 덮어써 최초 시각이 남지 않는다)", nullable = true) LocalDateTime firstVerifiedAt,
            @Schema(description = "최근 갱신 — C15-2 재인증 시각. 재인증한 적이 없으면 null", nullable = true) LocalDateTime renewedAt) {
    }

    @Schema(name = "AdminUserRecentOrders",
            description = "최근 주문 5건 — 어드민 주문 목록(GET /v1/admin/orders?userId=)과 같은 행 · 같은 집합이다. 「전체 주문 내역 보기 ›」는 그 목록으로 간다. "
                    + "상태 칸의 「반품완료」 · 「교환완료」는 groups[].claim.label(56 C2)이다. "
                    + "total 은 결제된 주문 전부(전액 취소 포함)라 누적 주문 수(취소만 남은 주문 제외)와 다를 수 있다. 탈퇴 회원도 보존 기록이라 그대로 내린다")
    public record RecentOrders(@Schema(example = "14") long total, List<AdminOrderDto.ListItem> content) {
    }

    @Schema(name = "AdminUserRecentInquiries",
            description = "최근 문의 3건 — 1:1 문의와 상품 문의(삭제 집행 제외)를 접수일 최신순으로 합친다. total 은 두 문의 합(inquiryCount 와 같다). "
                    + "탈퇴 후에도 문의 기록은 보존이라 그대로 내린다")
    public record RecentInquiries(@Schema(example = "3") long total, List<RecentInquiry> content) {
    }

    @Schema(name = "AdminUserRecentInquiry")
    public record RecentInquiry(
            @Schema(description = "ONE_TO_ONE(1:1 문의 관리) · PRODUCT(상품 문의 모니터링) — 행 클릭 시 이동할 화면") AdminUserInquiryKind kind,
            @Schema(example = "1:1") String kindLabel,
            Long inquiryId,
            @Schema(description = "1:1 은 INQ- · 상품 문의는 QNA- — 각 관리 화면과 같은 번호", example = "INQ-20260813-041") String inquiryNumber,
            @Schema(description = "유형 — 1:1 은 FAQ 분류 · 상품 문의는 문의 유형", example = "배송") String typeLabel,
            @Schema(description = "제목 — 제목 열이 없어 본문 앞부분(40자 · 줄바꿈 접음)을 쓴다", example = "세럼 배송이 언제 오나요?") String title,
            InquiryStatus status,
            @Schema(example = "답변 대기") String statusLabel,
            LocalDateTime createdAt) {
    }

    @Schema(name = "AdminUserAddresses",
            description = "배송지 — 기본 배송지 먼저 · 나머지는 최근 수정순(최근 사용 순서는 주문에 배송지 id 가 없어 낼 수 없다). 마스킹 중에는 시 · 군 · 구까지만 · "
                    + "상세주소 · 우편번호는 null · 수령인 · 연락처는 마스킹(「해제 후 조회」 · 본인확인 정보와 같은 열람 통제). 운영자는 수정 · 삭제하지 않는다. "
                    + "탈퇴는 파기라 destroyed = true · 목록은 빈 배열")
    public record Addresses(boolean destroyed, @Schema(example = "3") int total, List<Address> content) {
    }

    @Schema(name = "AdminUserAddress")
    public record Address(
            Long addressId,
            @Schema(description = "기본 배송지 여부") boolean defaultAddress,
            @Schema(description = "주소 — 마스킹 중 시 · 군 · 구까지", example = "서울특별시 마포구") String address,
            @Schema(description = "상세주소 — 해제 상태에서만", nullable = true) String detailAddress,
            @Schema(description = "우편번호 — 해제 상태에서만", nullable = true) String zipCode,
            @Schema(example = "홍*동") String recipientName,
            @Schema(example = "010-****-1234") String phoneNumber,
            @Schema(description = "최근 사용 주문일 — 주문에 배송지 id 가 없어 낼 수 없다(보류 · 항상 null)", nullable = true) LocalDateTime lastUsedOrderAt) {
    }

    @Schema(name = "AdminUserPendingChip", description = "레일 「진행 중」 칩 — 1건 이상만 내린다. 나머지는 FE 가 「그 외 0건」으로 접는다")
    public record PendingChip(
            @Schema(example = "OPEN_INQUIRY") UserPendingChip key,
            @Schema(example = "열린 문의") String label,
            @Schema(description = "열린 문의만 WARNING · 나머지 NEUTRAL", example = "WARNING") String tone,
            @Schema(description = "건수 — links[].count 의 합. 각 링크 목록을 그 query 로 부른 결과 건수와 같다(56 C1)", example = "1") long count,
            @Schema(description = "이동할 화면 — INQUIRIES · ORDERS · CLAIMS · REFUNDS(FE 가 userId 를 붙인다)", example = "INQUIRIES") String target,
            @Schema(description = "이동 목록(56 C1) — 칩이 가리키는 목록 화면 · 그 목록 API 에 그대로 붙일 쿼리(탭 · 상태 · userId) · 그 목록 건수. "
                    + "열린 문의만 1:1 문의(INQUIRIES)와 상품 문의(PRODUCT_INQUIRIES) 두 목록으로 갈릴 수 있고, 0건인 목록은 뺀다(하나면 그 목록으로 바로 간다). "
                    + "나머지 칩은 한 개다")
            List<PendingChipLink> links) {
    }

    @Schema(name = "AdminUserPendingChipLink",
            description = "칩 이동 목록 — target 목록 API 를 query 로 부르면 pageInfo.totalResults 가 count 와 같다. 기간 조건은 없다(클레임 목록은 userId 가 있으면 기본 1개월을 걸지 않는다)")
    public record PendingChipLink(
            @Schema(description = "목록 화면 — INQUIRIES(GET /v1/admin/inquiries) · PRODUCT_INQUIRIES(GET /v1/admin/product-inquiries) · "
                    + "ORDERS(GET /v1/admin/orders) · CLAIMS(GET /v1/admin/claims) · REFUNDS(GET /v1/admin/refunds)", example = "INQUIRIES")
            String target,
            @Schema(description = "목록 쿼리 파라미터 — 값은 문자열 · userId 포함. 열린 문의 {status: WAITING} · 배송중 {status: SHIPPING} · "
                    + "취소 요청 {tab: CANCEL, cancelRequested: true} · 반품·교환 {tab: ALL, openOnly: true} · 환불 실패 {tab: FAILED}",
                    example = "{\"status\": \"WAITING\", \"userId\": \"88231\"}")
            java.util.Map<String, String> query,
            @Schema(description = "그 목록 건수", example = "1") long count) {
    }

    @Schema(name = "AdminUserJudgement",
            description = "판단 메타 — 조치(정지) 근거라 우측 레일 액션 버튼 위에 둔다. 탈퇴 회원은 조치가 없어 거래 값이 null(「—」)이고 가입 후 경과 · 정지 이력만 남는다")
    public record Judgement(
            @Schema(description = "누적 주문 — totalOrderCount 와 같은 식", example = "14", nullable = true) Integer orderCount,
            @Schema(description = "누적 구매액 — totalPurchaseAmount 와 같은 순결제액", example = "842000", nullable = true) Long purchaseAmount,
            @Schema(description = "반품 · 교환이 접수된 주문 수(요청 취소 · 재발송비 결제 대기 제외 · 반려 종결 포함)", example = "2", nullable = true) Long claimOrderCount,
            @Schema(description = "반품 · 교환률(%) = claimOrderCount ÷ 누적 주문 · 소수 1자리. 주문이 없으면 null", example = "14.3", nullable = true) Double claimRate,
            SanctionHistory sanctions,
            Elapsed sinceJoined) {
    }

    @Schema(name = "AdminUserSanctionHistory", description = "정지 이력 「2회 · 직전 2026.05 · 반품 남용」 — user_sanction 행 기준(D15 · 옛 상태 변경 API 정지는 빠진다)")
    public record SanctionHistory(
            @Schema(description = "정지 횟수 — 지금 열린 정지 포함 · 0 이면 「없음」", example = "2") long count,
            @Schema(description = "직전 정지 — 해제된 정지 중 가장 최근 것(지금 열린 정지는 상태 카드가 보여 준다). 없으면 null", nullable = true) PastSanction last) {
    }

    @Schema(name = "AdminUserPastSanction")
    public record PastSanction(Long sanctionId, UserSuspensionReason reason, @Schema(example = "반품 남용") String reasonLabel,
                               LocalDateTime suspendedAt, LocalDateTime releasedAt) {
    }

    @Schema(name = "AdminUserElapsed", description = "가입 후 경과 「6개월 13일」 — 가입일 기준 달력 계산")
    public record Elapsed(@Schema(example = "6") int months, @Schema(example = "13") int days,
                          @Schema(description = "총 경과 일수", example = "196") long totalDays) {
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

        @Schema(description = "회원번호 — 목록 첫 열과 같은 번호", example = "CST-88231")
        private String memberNo;

        @Schema(description = "「‹ 이전 · 다음 ›」 — 회원번호 순 인접 소비자(목록 필터 · 정렬과 무관 · 약관 동의 전 GUEST 제외)")
        private AdminMemberNavigation navigation;

        @Schema(description = "닉네임", example = "홍길동")
        private String nickname;

        @Schema(description = "가입 채널", example = "GOOGLE")
        private ProviderType providerType;

        @Schema(description = "활동 상태", example = "NORMAL")
        private UserStatus status;

        @Schema(description = "생년월일 — identity.birthday 와 같은 값(마스킹 · 55 계획서 M3-2부터 원본을 내리지 않는다). identity 를 쓴다",
                example = "1990.**.**", deprecated = true)
        private String birthday;

        @Schema(description = "성별", example = "MALE")
        private String gender;

        @Schema(description = "기본 배송지 — 시 · 군 · 구까지만(상세주소는 열람 통제 대상). 없거나 탈퇴로 파기됐으면 null", example = "서울특별시 마포구")
        private String defaultAddress;

        @Schema(description = "최초 로그인 시간(가입일)", example = "2024-01-01T10:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "최근 로그인 — 탈퇴는 파기라 null(「— 파기됨」)", example = "2026-08-14T09:41:00", nullable = true)
        private LocalDateTime lastLoginAt;

        @Schema(description = "최근 로그인 기기 — 가장 최근 로그인 이력의 User-Agent 로 판별(인플루언서 상세와 같은 규칙). 이력이 없거나 탈퇴면 null",
                example = "IPHONE", nullable = true)
        private DeviceType lastLoginDevice;

        @Schema(description = "알림 수신 — 앱 푸시 켬/끔 · 광고성 수신 동의")
        private NotificationStatus notification;

        @Schema(description = "본인확인 정보 — 기본 마스킹 · 이 운영자의 열람 세션이 있으면 실값(M3-3)")
        private Identity identity;

        @Schema(description = "마스킹 해제 상태 — 해제 버튼 활성 · 해제됨 · 처리자 · 남은 시간 · 직전 사유")
        private Unmask unmask;

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

        @Schema(description = "누적 구매액 — 순결제액(결제된 주문 총액 − 그 결제의 PG 취소 성공액)", example = "684000")
        private Long totalPurchaseAmount;

        @Schema(description = "이번 달 구매액 — 결제월 기준 순결제액", example = "52100")
        private Long thisMonthPurchaseAmount;

        @Schema(description = "누적 주문 수 — 결제된 주문 중 취소되지 않은 상품이 남은 주문(목록과 같은 식)", example = "14")
        private Integer totalOrderCount;

        @Schema(description = "이번 달 주문 수 — 결제월 기준", example = "1")
        private Integer thisMonthOrderCount;

        @Schema(description = "평균 주문 금액 — 누적 구매액 ÷ 누적 주문 수(주문이 없으면 0)", example = "48857")
        private Long averageOrderAmount;

        @Schema(description = "최근 주문일 — 마지막 결제 시각. 주문이 없으면 null", example = "2026-10-03T10:12:00")
        private LocalDateTime lastOrderDate;

        @Schema(description = "상품 위시리스트 수", example = "12")
        private Long productWishlistCount;

        @Schema(description = "팔로우 쇼룸 수", example = "5")
        private Long followedShowroomCount;

        @Schema(description = "작성 리뷰 수", example = "7")
        private Long writtenReviewCount;

        @Schema(description = "문의 내역 수", example = "2")
        private Long inquiryCount;

        @Schema(description = "최근 주문 5건")
        private RecentOrders recentOrders;

        @Schema(description = "최근 문의 3건(1:1 · 상품 문의 합산)")
        private RecentInquiries recentInquiries;

        @Schema(description = "배송지 목록")
        private Addresses addresses;

        @Schema(description = "레일 「진행 중」 칩 — 1건 이상만. 빈 배열이면 「진행 중인 건 없음」")
        private List<PendingChip> pendingChips;

        @Schema(description = "레일 판단 메타 — 누적 주문 · 구매액 · 반품 · 교환률 · 가입 후 경과 · 정지 이력")
        private Judgement judgement;

        public static UserDetailResponse of(
                Users user,
                Long wishlistCount,
                Long followedShowroomCount,
                Long reviewCount,
                Long inquiryCount,
                AdminUserTradeReader.Trade trade,
                AdminUserDetailAssembler.Extras extras,
                StatusCard statusCard,
                MemoPage memos,
                HistorySummary histories) {

            return UserDetailResponse.builder()
                    .userId(user.getId())
                    .memberNo(extras.memberNo())
                    .navigation(extras.navigation())
                    .nickname(user.getNickname())
                    .providerType(user.getProviderType())
                    .status(user.getStatus())
                    .birthday(extras.identity().birthday())
                    .gender(user.getGender())
                    .defaultAddress(trade.defaultAddress())
                    .createdAt(user.getCreatedAt())
                    .lastLoginAt(extras.lastLoginAt())
                    .lastLoginDevice(extras.lastLoginDevice())
                    .notification(extras.notification())
                    .identity(extras.identity())
                    .unmask(extras.unmask())
                    .marketingAgree(user.isMarketingAgree())
                    .profileImageUrl(user.getProfileImageUrl())
                    .statusCard(statusCard)
                    .memos(memos)
                    .histories(histories)
                    .totalPurchaseAmount(trade.totalPurchaseAmount())
                    .thisMonthPurchaseAmount(trade.thisMonthPurchaseAmount())
                    .totalOrderCount(trade.totalOrderCount())
                    .thisMonthOrderCount(trade.thisMonthOrderCount())
                    .averageOrderAmount(trade.averageOrderAmount())
                    .lastOrderDate(trade.lastOrderDate())
                    .productWishlistCount(wishlistCount)
                    .followedShowroomCount(followedShowroomCount)
                    .writtenReviewCount(reviewCount)
                    .inquiryCount(inquiryCount)
                    .recentOrders(extras.recentOrders())
                    .recentInquiries(extras.recentInquiries())
                    .addresses(extras.addresses())
                    .pendingChips(extras.pendingChips())
                    .judgement(extras.judgement())
                    .build();
        }
    }
}
