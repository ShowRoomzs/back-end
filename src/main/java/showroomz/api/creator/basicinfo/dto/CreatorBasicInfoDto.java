package showroomz.api.creator.basicinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 스튜디오 기본정보 관리(§50 · 50 설계서) 응답 · 요청 스키마 — 시안 ui-studio-06-account 의 값 하나하나에 대응한다. */
public class CreatorBasicInfoDto {

    // ------------------------------------------------------------------ 계정 탭(S1 ~ S8)

    @Getter
    @Builder
    @Schema(description = "계정 탭 조회 — 본인 인증 정보 · 업무 이메일 · 로그인 수단 · 탈퇴 블록")
    public static class AccountResponse {
        private Identity identity;
        @Schema(description = "업무 이메일 — 승인 · 계약 · 정산 알림 수신 주소. 이 탭에서 셀프 수정하는 유일한 값", example = "soyeon@example.com")
        private String businessEmail;
        private Login login;
        private Withdrawal withdrawal;
    }

    @Getter
    @Builder
    @Schema(description = "본인 인증 정보 — 셀프 수정 불가 · 서버가 마스킹해 내린다")
    public static class Identity {
        @Schema(description = "실명 — 가운데 마스킹", example = "소*연")
        private String realName;
        @Schema(description = "생년월일 — 저장 형식 그대로", example = "2003-07-19")
        private String birthday;
        @Schema(description = "휴대폰 — 가운데 마스킹", example = "010-****-6416")
        private String phoneNumber;
    }

    @Getter
    @Builder
    @Schema(description = "로그인 수단 — 비밀번호가 없는 소셜 로그인")
    public static class Login {
        @Schema(example = "KAKAO")
        private String provider;
        @Schema(example = "카카오")
        private String providerLabel;
    }

    @Getter
    @Builder
    @Schema(description = "탈퇴 블록 — 상태별로 S4 · S5 · S7 · S8 을 그린다. 조건 행은 상태와 무관하게 항상 내린다")
    public static class Withdrawal {
        @Schema(description = "AVAILABLE(S5) · BLOCKED(S4) · PENDING(S7) · REJECTED(S8)", example = "BLOCKED")
        private String state;
        @Schema(example = "0")
        private int satisfiedCount;
        @Schema(example = "3")
        private int totalCount;
        private List<WithdrawalCondition> conditions;
        @Schema(description = "승인 처리 기한 — 영업일", example = "5")
        private int approvalDueBusinessDays;
        @Schema(description = "PENDING 일 때만", nullable = true)
        private PendingWithdrawal pendingRequest;
        @Schema(description = "REJECTED 일 때만", nullable = true)
        private WithdrawalRejection rejection;
    }

    @Getter
    @Builder
    public static class WithdrawalCondition {
        @Schema(example = "UNFINISHED_GROUP_BUYS")
        private String code;
        @Schema(example = "끝나지 않은 공구")
        private String label;
        @Schema(example = "2")
        private long count;
        private boolean satisfied;
        @Schema(example = "주문이 모두 종결되어야 합니다")
        private String resolution;
        @Schema(description = "상태별 건수 — 0 건 키는 내리지 않는다")
        private List<ConditionBreakdown> breakdown;
    }

    @Getter
    @Builder
    public static class ConditionBreakdown {
        @Schema(example = "IN_PROGRESS")
        private String key;
        @Schema(example = "진행중")
        private String label;
        @Schema(example = "1")
        private long count;
    }

    @Getter
    @Builder
    public static class PendingWithdrawal {
        private Long requestId;
        private LocalDateTime requestedAt;
        private String reasonText;
        @Schema(description = "승인 처리 기한(안내) — 무응답 자동 승인 없음")
        private LocalDate dueDate;
    }

    @Getter
    @Builder
    public static class WithdrawalRejection {
        private Long requestId;
        private LocalDateTime rejectedAt;
        @Schema(example = "SETTLEMENT_IN_PROGRESS")
        private String reasonCode;
        @Schema(example = "진행 중 정산 남음")
        private String reasonLabel;
        @Schema(description = "운영자가 남긴 안내", nullable = true)
        private String note;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "업무 이메일 저장")
    public static class UpdateBusinessEmailRequest {

        @NotBlank(message = "업무 이메일을 입력해 주세요.")
        @Size(max = 512, message = "업무 이메일은 512자 이내로 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "업무 이메일 — 도메인에 점이 있어야 한다(soyeon@example 은 오류)", example = "soyeon@example.com")
        private String businessEmail;

        /** 앞뒤 공백은 저장하지 않는다 — 검증도 다듬은 값으로 본다. */
        public void setBusinessEmail(String businessEmail) {
            this.businessEmail = businessEmail == null ? null : businessEmail.strip();
        }
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "탈퇴 승인 요청 — 사유 선택지가 정의되지 않아(§55 B-13) 고른 라벨 문자열을 그대로 보낸다")
    public static class WithdrawalRequestBody {
        @Schema(description = "탈퇴 사유 — 1 ~ 500자", example = "활동 중단")
        private String reason;

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    @Getter
    @Builder
    public static class WithdrawalRequestResponse {
        private Long requestId;
        private LocalDateTime requestedAt;
        private LocalDate dueDate;
    }

    // ------------------------------------------------------------------ 정산 정보 탭(T1 ~ T10)

    @Getter
    @Builder
    @Schema(description = "정산 정보 탭 조회 — 금액은 없다(정산 관리가 단일 소스 · §50-2)")
    public static class SettlementInfoResponse {
        private BusinessTypeInfo businessType;
        @Schema(description = "비사업자일 때만 — 사업자면 null(값이 남아 있어도 카드는 없다)", nullable = true)
        private ResidentNumberInfo residentNumber;
        @Schema(description = "사업자일 때만", nullable = true)
        private BusinessInfo business;
        @Schema(description = "사업자일 때만 — [발행 정보 복사] 재료(공급받는자 = SHOWROOMZ)", nullable = true)
        private TaxInvoiceRecipient taxInvoiceRecipient;
        private AccountInfo account;
        @Schema(description = "배너가 보이는 최근 사업자 구분 변경 요청 — 없으면 null", nullable = true)
        private BusinessTypeChangeBanner businessTypeChange;
        private SettlementActions actions;
    }

    @Getter
    @Builder
    public static class BusinessTypeInfo {
        @Schema(example = "INDIVIDUAL")
        private String value;
        @Schema(example = "개인 (비사업자)")
        private String label;
        @Schema(description = "최근 승인된 구분 변경의 처리 시각 — 없으면 null(온보딩 값)", nullable = true)
        private LocalDateTime confirmedAt;
    }

    @Getter
    @Builder
    public static class ResidentNumberInfo {
        @Schema(description = "뒤 7자리 전부 마스킹", example = "900101-*******")
        private String masked;
        @Schema(description = "등록 시각 — 이전 등록분은 null", nullable = true)
        private LocalDateTime registeredAt;
    }

    @Getter
    @Builder
    public static class BusinessInfo {
        @Schema(example = "123-45-67890")
        private String registrationNumber;
        private DocumentFile license;
    }

    @Getter
    @Builder
    @Schema(description = "제출 서류 — 파일명 · 크기 · 제출일은 모르면 null(온보딩분)")
    public static class DocumentFile {
        private String url;
        private String fileName;
        private Long fileSize;
        private LocalDateTime submittedAt;
    }

    @Getter
    @Builder
    @Schema(description = "세금계산서 공급받는자(SHOWROOMZ) — 정산 관리의 공급받는자 블록과 같은 값")
    public static class TaxInvoiceRecipient {
        private String businessName;
        private String registrationNumber;
        private String representative;
        private String address;
        private String taxEmail;
    }

    @Getter
    @Builder
    public static class AccountInfo {
        @Schema(example = "국민은행")
        private String bankName;
        @Schema(description = "뒤 6자리만 노출", example = "******456789")
        private String accountNumber;
        @Schema(description = "예금주 = 본인 인증 실명(가운데 마스킹) — 입력받지 않는다", example = "소*연")
        private String holderName;
        @Schema(description = "REAL_NAME(비사업자) · REAL_NAME_OR_COMPANY(사업자) — 안내 문구용", example = "REAL_NAME")
        private String holderRule;
        private DocumentFile bankbook;
        private AccountVerification verification;
    }

    @Getter
    @Builder
    public static class AccountVerification {
        @Schema(description = "UNVERIFIED · VERIFYING · VERIFIED · FAILED · CHANGE_REQUIRED", example = "VERIFIED")
        private String status;
        @Schema(example = "실명 확인 완료")
        private String label;
        @Schema(example = "SUCCESS")
        private String tone;
        private LocalDateTime verifiedAt;
        @Schema(description = "FAILED 일 때만 — 「예금주 불일치」 등", nullable = true)
        private String failReason;
    }

    @Getter
    @Builder
    @Schema(description = "사업자 구분 변경 요청 배너(T4 · T4a · T5 · T9 · T9a · T10)")
    public static class BusinessTypeChangeBanner {
        private Long requestId;
        @Schema(example = "CHG-2026-0031")
        private String requestCode;
        @Schema(description = "TO_BUSINESS · TO_INDIVIDUAL", example = "TO_BUSINESS")
        private String type;
        @Schema(description = "PENDING · APPROVED · REJECTED", example = "PENDING")
        private String status;
        private LocalDateTime requestedAt;
        private LocalDateTime processedAt;
        @Schema(description = "TO_BUSINESS 요청 값", nullable = true)
        private String businessRegistrationNumber;
        @Schema(description = "TO_INDIVIDUAL 요청 값(마스킹)", nullable = true)
        private String maskedResidentNumber;
        private DocumentFile evidence;
        @Schema(description = "REJECTED 일 때만", nullable = true)
        private RejectReason rejectReason;
        @Schema(nullable = true)
        private String rejectReasonDetail;
        @Schema(description = "승인 후 예금주 규칙 위반 — 상호 명의 계좌를 받기 전(§55 B-9)에는 항상 false")
        private boolean accountChangeRequiredOnApproval;
    }

    @Getter
    @Builder
    public static class RejectReason {
        private String code;
        private String label;
    }

    @Getter
    @Builder
    @Schema(description = "계좌 변경 확인 모달(T6) — 「확인이 끝날 때까지 지급 예정 1건(99,505원)의 입금이 보류됩니다」")
    public static class AccountChangeImpact {
        @Schema(description = "확정됐고 지시 전(예정 · 보류) — 확인이 끝날 때까지 기다린다", example = "1")
        private long pendingPayoutCount;
        @Schema(example = "99505")
        private long pendingPayoutAmount;
        @Schema(description = "이미 지급 지시가 나가 이전 계좌로 지급되는 건", example = "0")
        private long inFlightPayoutCount;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "정산 계좌 변경 — 예금주는 받지 않는다(본인 인증 실명)")
    public static class ChangeSettlementAccountRequest {

        @NotBlank(message = "은행을 선택해 주세요.")
        @Size(min = 3, max = 3, message = "은행 코드는 3자리여야 합니다.")
        @Schema(description = "은행 표준 코드(GET /v1/common/banks)", example = "088")
        private String bankCode;

        @NotBlank(message = "계좌번호를 입력해 주세요.")
        @Pattern(regexp = "^[0-9]{10,16}$", message = "계좌번호는 10~16자리 숫자로 입력해 주세요.")
        @Schema(description = "계좌번호 — 하이픈 없이 숫자 10~16자리", example = "1101234567")
        private String accountNumber;

        @NotBlank(message = "통장 사본을 첨부해 주세요.")
        @Schema(description = "통장 사본 URL — POST /v1/creator/images?type=CREATOR_DOCUMENT", example = "https://cdn…/creator-document/9f2c.jpg")
        private String bankbookImageUrl;

        @Size(max = 255, message = "파일명은 255자 이내여야 합니다.")
        @Schema(description = "통장 사본 원본 파일명(표시용)", example = "통장사본_신한은행.jpg", nullable = true)
        private String bankbookFileName;

        public void setBankCode(String bankCode) {
            this.bankCode = bankCode;
        }

        public void setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
        }

        public void setBankbookImageUrl(String bankbookImageUrl) {
            this.bankbookImageUrl = bankbookImageUrl;
        }

        public void setBankbookFileName(String bankbookFileName) {
            this.bankbookFileName = bankbookFileName;
        }
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "사업자 구분 변경 요청(T7 개인사업자로 · T8 비사업자로) — 운영자 확인 후 반영 · 증빙은 CREATOR_DOCUMENT 로 먼저 올린다")
    public static class BusinessTypeChangeRequest {

        @Schema(description = "바꿀 구분 — BUSINESS(T7) · INDIVIDUAL(T8). 현재 구분과 같으면 409", example = "BUSINESS")
        private showroomz.domain.member.creator.type.CreatorBusinessType targetType;

        @Schema(description = "T7 필수 — 하이픈 포함", example = "123-45-67890", nullable = true)
        private String businessRegistrationNumber;

        @Schema(description = "T8 필수 — 응답 · 로그 어디에도 원문을 남기지 않는다", example = "900101-2345678", nullable = true)
        private String residentRegistrationNumber;

        @Schema(description = "증빙 URL — T7 사업자등록증 · T8 폐업사실증명원", example = "https://cdn…/creator-document/….pdf")
        private String evidenceFileUrl;

        @Schema(example = "사업자등록증_뷰티소연.pdf")
        private String evidenceFileName;

        @Schema(description = "바이트 — 최대 10MB", example = "1258291")
        private Long evidenceFileSize;

        public void setTargetType(showroomz.domain.member.creator.type.CreatorBusinessType targetType) {
            this.targetType = targetType;
        }

        public void setBusinessRegistrationNumber(String businessRegistrationNumber) {
            this.businessRegistrationNumber = businessRegistrationNumber;
        }

        public void setResidentRegistrationNumber(String residentRegistrationNumber) {
            this.residentRegistrationNumber = residentRegistrationNumber;
        }

        public void setEvidenceFileUrl(String evidenceFileUrl) {
            this.evidenceFileUrl = evidenceFileUrl;
        }

        public void setEvidenceFileName(String evidenceFileName) {
            this.evidenceFileName = evidenceFileName;
        }

        public void setEvidenceFileSize(Long evidenceFileSize) {
            this.evidenceFileSize = evidenceFileSize;
        }

        /** 요청 로깅에 고유식별정보가 남지 않게 — 온보딩 DTO 와 같은 규칙. */
        @Override
        public String toString() {
            return "BusinessTypeChangeRequest(targetType=" + targetType + ", businessRegistrationNumber="
                    + businessRegistrationNumber + ", residentRegistrationNumber="
                    + (residentRegistrationNumber == null ? null : "****") + ")";
        }
    }

    @Getter
    @Builder
    public static class BusinessTypeChangeCreated {
        private Long requestId;
        @Schema(example = "CHG-2026-0031")
        private String requestCode;
        @Schema(example = "TO_BUSINESS")
        private String type;
        @Schema(example = "PENDING")
        private String status;
        private LocalDateTime requestedAt;
    }

    @Getter
    @Builder
    public static class SettlementActions {
        @Schema(description = "REQUEST_TO_BUSINESS · REQUEST_TO_INDIVIDUAL · CANCEL — 모든 상태에서 같은 자리", example = "REQUEST_TO_BUSINESS")
        private String businessTypeChange;
        private boolean accountChange;
    }
}
