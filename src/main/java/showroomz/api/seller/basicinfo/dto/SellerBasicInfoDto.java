package showroomz.api.seller.basicinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.seller.changerequest.dto.ChangeRequestBannerResponse;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.type.BrandWithdrawalReason;

import java.time.LocalDateTime;
import java.util.List;

/** 부록 A(§15) 응답·요청 스키마. 시안에 렌더링되는 값 하나하나를 필드에 대응시킨 계약이다. */
public class SellerBasicInfoDto {

    @Getter
    @Builder
    @Schema(description = "사업자 정보 탭 조회 (A-1)")
    public static class BusinessInfoResponse {
        @Schema(description = "브랜드명", example = "코코브라운")
        private String brandName;
        @Schema(description = "마켓 상태 — ACTIVE · DORMANT · SUSPENDED · WITHDRAWN", example = "ACTIVE")
        private MarketStatus brandStatus;
        @Schema(description = "계정 이용 정지 중일 때만 — 정지 배너 재료(52 설계서 7절). 상세 사유는 내리지 않는다", nullable = true)
        private SanctionInfo sanction;
        @Schema(description = "사업자 유형 — ②변경 요청 항목", example = "법인사업자")
        private String businessType;
        @Schema(description = "브랜드명(사업자 정보 행) — ②변경 요청 항목", example = "코코브라운")
        private String marketName;
        @Schema(description = "대표자명 — ②변경 요청 항목", example = "김대표")
        private String representativeName;
        @Schema(description = "사업자등록증 상호 — ②변경 요청 항목", example = "(주)코코브라운")
        private String companyName;
        @Schema(description = "사업자등록번호 — ③변경 불가", example = "123-45-67890")
        private String businessRegistrationNumber;
        @Schema(description = "업태 — ②변경 요청 항목", example = "도소매")
        private String businessCondition;
        @Schema(description = "사업장 주소 + 상세주소 결합 단일 문자열 — ②변경 요청 항목", example = "서울시 서초구 서초대로 1 3층")
        private String businessAddress;
        @Schema(description = "통신판매업 신고번호 — ②변경 요청 항목", example = "2024-서울서초-0001")
        private String mailOrderRegNumber;
        @Schema(description = "tax 확인용 이메일 — ①직접 수정", example = "tax@cocobrown.example")
        private String taxEmail;
        @Schema(description = "브랜드 사이트 링크 — ①직접 수정 · 없으면 null", example = "https://cocobrown.example", nullable = true)
        private String brandSiteUrl;
        @Schema(description = "입점 심사 첨부 서류(있는 것만)")
        private List<ReviewDocumentItem> reviewDocuments;
        @Schema(description = "사업자 정보 변경 요청 배너 — 검토 대기 또는 확인 전 처리 결과 1건 · 없으면 null", nullable = true)
        private ChangeRequestBannerResponse changeRequest;
    }

    @Getter
    @Builder
    @Schema(description = "계정 이용 정지 — 로그인은 되고 새로 벌이는 일만 막힌다(§51-1)")
    public static class SanctionInfo {
        @Schema(example = "SETTLEMENT_INFO_MISMATCH")
        private String reason;
        @Schema(example = "정산 정보 불일치")
        private String reasonLabel;
        @Schema(description = "정지 시각", example = "2026-10-16T11:00:00")
        private java.time.LocalDateTime suspendedAt;
        @Schema(description = "브랜드 몫 지급 정지 — 해제 다음 영업일 지급", example = "true")
        private boolean payoutHeld;
    }

    @Getter
    @Builder
    @Schema(description = "심사 첨부 서류(조회 전용)")
    public static class ReviewDocumentItem {
        @Schema(description = "BUSINESS_LICENSE · MAIL_ORDER_REPORT · BANKBOOK", example = "BUSINESS_LICENSE")
        private String documentType;
        @Schema(example = "사업자등록증")
        private String label;
        @Schema(example = "https://cdn…/license.jpg")
        private String fileUrl;
        @Schema(description = "확장자 — 미리보기 분기용", example = "jpg")
        private String extension;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사업자 정보 직접 수정 요청")
    public static class UpdateBusinessInfoRequest {

        @NotBlank(message = "tax 확인용 이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다. (예: example@domain.com)")
        @Schema(description = "tax 확인용 이메일", example = "tax@brand.com")
        private String taxEmail;

        @Pattern(regexp = "^$|^https?://.+$", message = "올바른 주소 형식이 아닙니다. (예: https://example.com)")
        @Schema(description = "브랜드 사이트 링크. 선택 입력 — 미입력 시 소비자 앱에 버튼이 노출되지 않는다.", example = "https://ohh-cosmetic.com")
        private String brandSiteUrl;
    }

    @Getter
    @Builder
    @Schema(description = "정산 계좌 탭 조회 (A-2)")
    public static class SettlementInfoResponse {
        @Schema(description = "은행명", example = "신한은행")
        private String bankName;
        @Schema(description = "뒤 6자리만 노출한 계좌번호", example = "******383841")
        private String maskedAccountNumber;
        @Schema(description = "예금주", example = "(주)코코브라운")
        private String accountHolder;
        @Schema(description = "정산 계좌 변경 요청 배너 — 검토 대기 또는 확인 전 처리 결과 1건 · 없으면 null", nullable = true)
        private ChangeRequestBannerResponse changeRequest;
    }

    @Getter
    @Builder
    @Schema(description = "담당자·CS 탭 조회 (A-3)")
    public static class ManagerInfoResponse {
        @Schema(description = "판매 담당자 이름", example = "김담당")
        private String managerName;
        @Schema(description = "판매 담당자 연락처(내부 관리용)", example = "010-1234-5678")
        private String managerContact;
        @Schema(description = "고객센터 전화번호(소비자 노출)", example = "02-1234-5678")
        private String csNumber;
        @Schema(description = "반품 수취 주소")
        private ReturnAddress returnAddress;
    }

    @Getter
    @Builder
    @Schema(description = "반품 수취 주소(카카오 주소 API 연동 — address는 readonly)")
    public static class ReturnAddress {
        @Schema(description = "반품 수취인", example = "코코브라운 물류")
        private String recipientName;
        @Schema(description = "반품 수취인 연락처(외부 노출)", example = "010-9876-5432")
        private String recipientContact;
        @Schema(description = "주소", example = "경기도 성남시 분당구 판교로 1")
        private String address;
        @Schema(description = "상세주소", example = "B동 2층")
        private String detailAddress;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "담당자·CS 탭 일괄 저장 요청 (7필드)")
    public static class UpdateManagerInfoRequest {

        @NotBlank(message = "판매 담당자 이름을 입력해 주세요.")
        @Schema(description = "판매 담당자 이름", example = "김담당")
        private String managerName;

        @NotBlank(message = "판매 담당자 연락처는 필수입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "올바른 핸드폰 번호 형식으로 입력해 주세요. (예: 010-0000-0000)")
        @Schema(description = "판매 담당자 연락처(내부 관리용, 010 11자리 고정)", example = "010-1234-5678")
        private String managerContact;

        @NotBlank(message = "고객센터 전화번호는 필수입니다.")
        @Pattern(regexp = "^(\\d{4}-\\d{4}|\\d{2,4}-\\d{3,4}-\\d{4}|\\d{3,4})$", message = "올바른 전화번호 형식으로 입력해 주세요. (예: 02-1234-5678)")
        @Schema(description = "고객센터 전화번호", example = "02-1234-5678")
        private String csNumber;

        @NotBlank(message = "수취인 이름은 필수입니다.")
        @Schema(description = "반품 수취인 이름", example = "김담당")
        private String recipientName;

        @NotBlank(message = "수취인 연락처는 필수입니다.")
        @Pattern(regexp = "^01(?:0|1|[6-9])-\\d{3,4}-\\d{4}$", message = "올바른 휴대폰 번호 형식으로 입력해 주세요. (예: 010-1234-5678)")
        @Schema(description = "반품 수취인 연락처(외부 노출용, 구 통신사 번호 허용)", example = "010-1234-5678")
        private String recipientContact;

        @NotBlank(message = "주소를 검색해 입력해 주세요.")
        @Schema(description = "반품 수취 주소(카카오 주소 API, readonly)", example = "서울특별시 강남구 테헤란로 123")
        private String address;

        @NotBlank(message = "상세 주소를 입력해 주세요.")
        @Schema(description = "반품 수취 상세주소", example = "4층 401호")
        private String detailAddress;
    }

    @Getter
    @Builder
    @Schema(description = "계정 탭 조회 (A-4)")
    public static class AccountInfoResponse {
        @Schema(description = "로그인 이메일", example = "brand@example.com")
        private String loginEmail;
        @Schema(description = "지금 로그인 이메일을 바꿀 수 있는가 — 월 1회 제한(마지막 변경 + 1개월) 통과", example = "true")
        private boolean emailChangeable;
        @Schema(description = "마지막 이메일 변경 시각 — 바꾼 적 없으면 null", nullable = true)
        private LocalDateTime lastEmailChangedAt;
        @Schema(description = "다음 변경 가능 시각 — 바꾼 적 없으면 null", nullable = true)
        private LocalDateTime nextEmailChangeableAt;
        @Schema(description = "탈퇴 블록 — 신청 · 승인제(§51-5). 마켓이 없는 계정은 null", nullable = true)
        private Withdrawal withdrawal;
    }

    // ------------------------------------------------------------------ 탈퇴 신청(§51-5 · 52 설계서 4절 · 정합 계획서 R0-1)

    @Getter
    @Builder
    @Schema(description = "탈퇴 블록 — 조건 행은 상태와 무관하게 항상 내린다. 판정식은 어드민 승인 재검증과 같다")
    public static class Withdrawal {
        @Schema(description = "AVAILABLE(신청 가능) · BLOCKED(조건 미충족) · PENDING(심사 중)", example = "BLOCKED")
        private String state;
        @Schema(description = "충족한 조건 수 — 「N / 4 충족」", example = "3")
        private int satisfiedCount;
        @Schema(description = "조건 수(항상 4)", example = "4")
        private int totalCount;
        @Schema(description = "조건 4행 — ACTIVE_GROUP_BUYS · CONTRACTS_BEFORE_CONCLUSION · UNCLOSED_ORDERS · UNSETTLED")
        private List<WithdrawalCondition> conditions;
        @Schema(description = "PENDING 일 때만", nullable = true)
        private PendingWithdrawal pendingRequest;
        @Schema(description = "가장 최근 신청이 반려였을 때만 — 즉시 재신청할 수 있다", nullable = true)
        private WithdrawalRejection lastRejection;
        @Schema(description = "신청 사유 선택지 — 선택하지 않으면 reason 을 비운다")
        private List<ReasonOption> reasonOptions;
    }

    @Getter
    @Builder
    @Schema(description = "탈퇴 조건 1행")
    public static class WithdrawalCondition {
        @Schema(example = "ACTIVE_GROUP_BUYS")
        private String code;
        @Schema(example = "진행 중 · 준비 중 공구")
        private String label;
        @Schema(description = "남은 건수 — 0 이면 충족", example = "1")
        private long count;
        @Schema(description = "count == 0", example = "false")
        private boolean satisfied;
    }

    @Getter
    @Builder
    @Schema(description = "심사 중인 탈퇴 신청(4-I)")
    public static class PendingWithdrawal {
        @Schema(description = "탈퇴 신청 ID — 취소 API 경로 값", example = "31")
        private Long requestId;
        @Schema(description = "신청 시각", example = "2026-10-14T14:20:00")
        private LocalDateTime requestedAt;
        @Schema(description = "사유 코드 — 선택하지 않았으면 null", example = "OTHER_CHANNEL_FOCUS", nullable = true)
        private String reason;
        @Schema(description = "사유 라벨 — 미선택이면 「선택하지 않음」", example = "다른 채널 집중")
        private String reasonLabel;
        @Schema(description = "남기실 말씀", example = "그동안 감사했습니다.", nullable = true)
        private String message;
        @Schema(description = "신청 시점 담당자명 스냅샷", example = "김담당")
        private String requesterName;
        @Schema(description = "신청자 — 현재 로그인 이메일(대기 중에는 바뀌지 않는다)", example = "brand@example.com")
        private String requesterEmail;
    }

    @Getter
    @Builder
    @Schema(description = "가장 최근 반려 결과 — 확인 처리가 없어 다음 신청 전까지 내린다")
    public static class WithdrawalRejection {
        @Schema(description = "반려된 탈퇴 신청 ID", example = "31")
        private Long requestId;
        @Schema(description = "반려 시각", example = "2026-10-16T11:00:00")
        private LocalDateTime rejectedAt;
        @Schema(example = "UNCLOSED_ORDERS")
        private String reasonCode;
        @Schema(example = "미종결 주문 잔존")
        private String reasonLabel;
        @Schema(description = "운영자가 남긴 안내", nullable = true)
        private String note;
    }

    @Getter
    @Builder
    @Schema(description = "탈퇴 사유 선택지")
    public static class ReasonOption {
        @Schema(example = "BUSINESS_CLOSED")
        private String code;
        @Schema(example = "사업 종료")
        private String label;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "탈퇴 신청 — 사유 · 남기실 말씀 모두 선택")
    public static class WithdrawalRequestBody {
        @Schema(description = "BrandWithdrawalReason — 선택하지 않으면 null", example = "OTHER_CHANNEL_FOCUS", nullable = true)
        private BrandWithdrawalReason reason;

        @Size(max = 1000, message = "남기실 말씀은 1000자 이내로 입력해 주세요.")
        @Schema(description = "남기실 말씀 — 1000자 이내", example = "그동안 감사했습니다.", nullable = true)
        private String message;
    }

    @Getter
    @Builder
    @Schema(description = "탈퇴 신청 접수 결과")
    public static class WithdrawalRequestResponse {
        @Schema(description = "탈퇴 신청 ID", example = "31")
        private Long requestId;
        @Schema(description = "신청 시각", example = "2026-10-14T14:20:00")
        private LocalDateTime requestedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "비밀번호 변경 요청")
    public static class ChangePasswordRequest {

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        @Schema(description = "현재 비밀번호", example = "OldPass1!")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,16}$",
                message = "8~16자 영문·숫자·특수문자 조합으로 입력해 주세요.")
        @Schema(description = "새 비밀번호 — 8~16자 · 영문 · 숫자 · 특수문자(@$!%*#?&) 각 1자 이상", example = "NewPass2@")
        private String newPassword;

        @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
        @Schema(description = "새 비밀번호 확인 — newPassword 와 같아야 한다", example = "NewPass2@")
        private String newPasswordConfirm;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "로그인 이메일 변경 요청")
    public static class ChangeEmailRequest {

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        @Schema(description = "현재 비밀번호 — 본인 확인", example = "NewPass2@")
        private String currentPassword;

        @NotBlank(message = "변경할 이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Schema(description = "새 로그인 이메일 — 지금 이메일과 달라야 하고 다른 계정이 쓰지 않아야 한다", example = "partner@cocobrown.example")
        private String newEmail;
    }

    @Getter
    @Builder
    @Schema(description = "배송·반품 정책 탭 조회 — 발송 기한 · 배송비. 반품 수취 주소는 담당자·CS 탭에 있다")
    public static class DeliveryPolicyResponse {
        @Schema(description = "발송 기한 N — 공구 마감 후 N영업일(주말·공휴일 제외 · 1~7)", example = "3")
        private Integer shippingLeadDays;
        @Schema(description = "소비자 상품 상세에 자동 표시되는 문구", example = "공구 마감 후 3영업일 이내 발송 (주말·공휴일 제외)")
        private String shipDueText;
        @Schema(description = "기본 배송비 — 반품 배송비 차감 · 교환 재발송비도 이 값이다(별도 반품비·교환비 없음)", example = "3000")
        private Integer defaultDeliveryFee;
        @Schema(description = "무료배송 기준금액 — 없으면 null", example = "50000", nullable = true)
        private Integer freeShippingThreshold;
        @Schema(description = "제주 추가비", example = "5000")
        private Integer jejuSurcharge;
        @Schema(description = "도서산간 추가비 — 제주 제외", example = "5000")
        private Integer remoteAreaSurcharge;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "배송·반품 정책 저장 — 바꾼 값은 이후 접수되는 주문에만 적용된다(이미 접수된 주문은 주문 시점 값)")
    public static class UpdateDeliveryPolicyRequest {
        @jakarta.validation.constraints.NotNull(message = "발송 기한을 입력해 주세요.")
        @jakarta.validation.constraints.Min(value = 1, message = "발송 기한은 1~7영업일로 입력해 주세요.")
        @jakarta.validation.constraints.Max(value = 7, message = "발송 기한은 1~7영업일로 입력해 주세요.")
        @Schema(description = "발송 기한 N — 공구 마감 후 N영업일 · 1~7", example = "3")
        private Integer shippingLeadDays;

        @jakarta.validation.constraints.NotNull(message = "기본 배송비를 입력해 주세요.")
        @jakarta.validation.constraints.Min(value = 0, message = "기본 배송비는 0 이상이어야 합니다.")
        @Schema(description = "기본 배송비", example = "3000")
        private Integer defaultDeliveryFee;

        @jakarta.validation.constraints.Min(value = 0, message = "무료배송 기준금액은 0 이상이어야 합니다.")
        @Schema(description = "무료배송 기준금액 — 비우거나 0 이면 무료배송 없음(0 은 null 로 저장)", example = "50000", nullable = true)
        private Integer freeShippingThreshold;

        @jakarta.validation.constraints.Min(value = 0, message = "제주 추가비는 0 이상이어야 합니다.")
        @Schema(description = "제주 추가비 — 비우면 0", example = "5000", nullable = true)
        private Integer jejuSurcharge;

        @jakarta.validation.constraints.Min(value = 0, message = "도서산간 추가비는 0 이상이어야 합니다.")
        @Schema(description = "도서산간 추가비 — 제주 제외 · 비우면 0", example = "5000", nullable = true)
        private Integer remoteAreaSurcharge;
    }
}
