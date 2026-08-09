package showroomz.api.seller.basicinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.seller.changerequest.dto.ChangeRequestBannerResponse;
import showroomz.domain.market.type.MarketStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 부록 A(§15) 응답·요청 스키마. 시안에 렌더링되는 값 하나하나를 필드에 대응시킨 계약이다. */
public class SellerBasicInfoDto {

    @Getter
    @Builder
    @Schema(description = "사업자 정보 탭 조회 (A-1)")
    public static class BusinessInfoResponse {
        private String brandName;
        private MarketStatus brandStatus;
        private String businessType;
        private String marketName;
        private String representativeName;
        private String companyName;
        private String businessRegistrationNumber;
        private String businessCondition;
        @Schema(description = "사업장 주소 + 상세주소 결합 단일 문자열")
        private String businessAddress;
        private String mailOrderRegNumber;
        private String taxEmail;
        private String brandSiteUrl;
        private List<ReviewDocumentItem> reviewDocuments;
        private ChangeRequestBannerResponse changeRequest;
    }

    @Getter
    @Builder
    @Schema(description = "심사 첨부 서류(조회 전용)")
    public static class ReviewDocumentItem {
        private String documentType;
        private String label;
        private String fileUrl;
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
        private String bankName;
        @Schema(description = "뒤 6자리만 노출한 계좌번호")
        private String maskedAccountNumber;
        private String accountHolder;
        private ChangeRequestBannerResponse changeRequest;
    }

    @Getter
    @Builder
    @Schema(description = "담당자·CS 탭 조회 (A-3)")
    public static class ManagerInfoResponse {
        private String managerName;
        private String managerContact;
        private String csNumber;
        private ReturnAddress returnAddress;
    }

    @Getter
    @Builder
    @Schema(description = "반품 수취 주소(카카오 주소 API 연동 — address는 readonly)")
    public static class ReturnAddress {
        private String recipientName;
        private String recipientContact;
        private String address;
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
        private String loginEmail;
        private boolean emailChangeable;
        private LocalDateTime lastEmailChangedAt;
        private LocalDateTime nextEmailChangeableAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "비밀번호 변경 요청")
    public static class ChangePasswordRequest {

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,16}$",
                message = "8~16자 영문·숫자·특수문자 조합으로 입력해 주세요.")
        private String newPassword;

        @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
        private String newPasswordConfirm;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "로그인 이메일 변경 요청")
    public static class ChangeEmailRequest {

        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        private String currentPassword;

        @NotBlank(message = "변경할 이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        private String newEmail;
    }
}
