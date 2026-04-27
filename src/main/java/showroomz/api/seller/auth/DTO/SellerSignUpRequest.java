package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "관리자(판매자) 회원가입 요청")
public class SellerSignUpRequest {

    // 1. 계정 정보
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Schema(description = "아이디(이메일)", example = "admin@showroomz.shop")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    // 8~16자, 영문+숫자+특수문자 조합
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,16}$", 
             message = "비밀번호는 8~16자의 영문자, 숫자, 특수문자를 포함해야 합니다.")
    @Schema(description = "비밀번호", example = "Admin123!")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
    @Schema(description = "비밀번호 재입력", example = "Admin123!")
    private String passwordConfirm;

    // 2. 셀러 정보
    @NotBlank(message = "판매 담당자 이름은 필수 입력값입니다.")
    @Schema(description = "판매 담당자 이름", example = "김담당")
    private String sellerName;

    @NotBlank(message = "연락처는 필수 입력값입니다.")
    // 일반적인 휴대폰 번호 형식 (010-1234-5678 또는 01012345678)
    @Pattern(regexp = "^01(?:0|1|[6-9])(?:\\d{3}|\\d{4})\\d{4}$|^01(?:0|1|[6-9])-(?:\\d{3}|\\d{4})-\\d{4}$",
             message = "올바른 휴대폰 번호 형식이 아닙니다.")
    @Schema(description = "판매 담당자 연락처", example = "010-1234-5678")
    private String sellerContact;

    // 3. 마켓 정보
    @NotBlank(message = "마켓명은 필수 입력값입니다.")
    // 공백 불가, 특수문자 불가, 한/영 혼용 불가 (한글만 or 영문만, 숫자는 허용한다고 가정)
    @Pattern(regexp = "^([가-힣0-9]+|[a-zA-Z0-9]+)$", 
             message = "마켓명은 공백과 특수문자를 사용할 수 없으며, 한글 또는 영문 중 하나만 사용해야 합니다.")
    @Schema(description = "마켓명", example = "쇼룸즈")
    private String marketName;

    @NotBlank(message = "고객센터 전화번호는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
    @Schema(description = "고객센터 전화번호", example = "02-1234-5678")
    private String csNumber;

    // 4. 사업자 정보
    @NotBlank(message = "사업자 구분을 선택해주세요.")
    @Schema(description = "사업자 구분 (일반과세자 / 간이과세자 / 면세사업자 / 법인 사업자)", example = "일반과세자")
    private String businessType;

    @NotBlank(message = "대표자명을 입력해주세요.")
    @Schema(description = "사업자등록증상의 대표자 이름", example = "홍길동")
    private String representativeName;

    @NotBlank(message = "대표자 연락처를 입력해주세요.")
    @Pattern(regexp = "^01(?:0|1|[6-9])-\\d{3,4}-\\d{4}$", message = "올바른 핸드폰 번호 형식으로 입력해주세요. (예: 010-0000-0000)")
    @Schema(description = "문자 수신 가능한 대표자 핸드폰 번호", example = "010-1234-5678")
    private String representativeContact;

    @NotBlank(message = "사업자등록증 상호명을 입력해주세요.")
    @Schema(description = "사업자등록증 상호명", example = "쇼룸즈 주식회사")
    private String companyName;

    @NotBlank(message = "사업자등록번호를 입력해주세요.")
    @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "올바른 사업자등록번호 형식으로 입력해주세요. (예: 000-00-00000)")
    @Schema(description = "사업자등록번호 (- 포함)", example = "123-45-67890")
    private String businessRegistrationNumber;

    @NotBlank(message = "업태를 입력해주세요.")
    @Schema(description = "사업자등록증에 기재된 대표 업태", example = "소매업")
    private String businessCondition;

    @NotBlank(message = "사업장 주소를 입력해주세요.")
    @Schema(description = "사업장 주소", example = "서울특별시 강남구 테헤란로 123")
    private String businessAddress;

    @NotBlank(message = "상세주소를 입력해주세요.")
    @Schema(description = "사업장 상세주소", example = "2층 201호")
    private String detailAddress;

    @NotBlank(message = "이메일 주소를 입력해주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다. (예: example@domain.com)")
    @Schema(description = "세금계산서(tax) 확인용 이메일", example = "tax@showroomz.shop")
    private String taxEmail;

    @NotBlank(message = "사업자등록증 사본을 첨부해주세요.")
    @Schema(description = "사업자등록증 사본 이미지 URL", example = "https://s3.ap-northeast-2.amazonaws.com/...")
    private String businessLicenseImageUrl;

    @Schema(description = "통신판매업신고증 사본 이미지 URL")
    private String mailOrderRegImageUrl;

    @Schema(description = "통신판매업신고번호 - 년도", example = "2024")
    @Pattern(regexp = "^\\d{4}$", message = "년도는 4자리 숫자로 입력해주세요. (예: 2024)")
    private String mailOrderRegNumberYear;

    @Schema(description = "통신판매업신고번호 - 지역", example = "서울강남")
    private String mailOrderRegNumberRegion;

    @Schema(description = "통신판매업신고번호 - 일련번호", example = "12345")
    private String mailOrderRegNumberSeq;

    // 5. 정산 정보
    @NotBlank(message = "은행명을 선택해주세요.")
    @Schema(description = "정산 계좌 은행명", example = "국민은행")
    private String bankName;

    @NotBlank(message = "예금주명을 입력해주세요.")
    @Schema(description = "정산 계좌 예금주명 (법인일 경우 법인명)", example = "홍길동")
    private String accountHolder;

    @NotBlank(message = "계좌번호를 입력해주세요.")
    @Pattern(regexp = "^[0-9-]+$", message = "올바른 계좌번호 형식으로 입력해주세요.")
    @Schema(description = "정산 계좌번호 (- 포함)", example = "111-2222-3333")
    private String accountNumber;

    @NotBlank(message = "통장 사본을 첨부해주세요.")
    @Schema(description = "정산 계좌 통장 사본 이미지 URL")
    private String bankbookImageUrl;

    // 6. 약관 동의
    @AssertTrue(message = "개인정보처리방침에 동의해야 합니다.")
    @Schema(description = "개인정보처리방침 동의 여부", example = "true")
    private Boolean agreePrivacyPolicy;

    @AssertTrue(message = "서비스 이용 약관에 동의해야 합니다.")
    @Schema(description = "서비스 이용 약관 동의 여부", example = "true")
    private Boolean agreeTermsOfService;

    @AssertTrue(message = "서비스 운영정책에 동의해야 합니다.")
    @Schema(description = "서비스 운영정책 동의 여부", example = "true")
    private Boolean agreeOperationPolicy;

    @AssertTrue(message = "통신판매업신고증 사본을 첨부해주세요.")
    @Schema(hidden = true)
    public boolean isValidMailOrderImage() {
        if ("간이과세자".equals(this.businessType)) {
            return true;
        }
        return mailOrderRegImageUrl != null && !mailOrderRegImageUrl.trim().isEmpty();
    }

    @AssertTrue(message = "통신판매업신고번호를 모두 입력해주세요.")
    @Schema(hidden = true)
    public boolean isValidMailOrderNumber() {
        if ("간이과세자".equals(this.businessType)) {
            return true;
        }
        return mailOrderRegNumberYear != null && !mailOrderRegNumberYear.trim().isEmpty()
                && mailOrderRegNumberRegion != null && !mailOrderRegNumberRegion.trim().isEmpty()
                && mailOrderRegNumberSeq != null && !mailOrderRegNumberSeq.trim().isEmpty();
    }
}