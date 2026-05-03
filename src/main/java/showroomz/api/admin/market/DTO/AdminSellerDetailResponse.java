package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "관리자용 판매자 상세 검토 정보 응답")
public class AdminSellerDetailResponse {

    @Schema(description = "판매자 계정 이메일", example = "seller@example.com")
    private String email;

    @Schema(description = "마켓명", example = "쇼룸즈")
    private String marketName;

    @Schema(description = "판매자 승인 상태", example = "PENDING")
    private String status;

    @Schema(description = "사업자 구분", example = "개인사업자")
    private String businessType;

    @Schema(description = "대표자명", example = "홍길동")
    private String representativeName;

    @Schema(description = "대표자 연락처", example = "010-1234-5678")
    private String representativeContact;

    @Schema(description = "사업자등록증 상호명", example = "(주)쇼룸즈")
    private String businessCompanyName;

    @Schema(description = "사업자 등록번호", example = "123-45-67890")
    private String businessRegistrationNumber;

    @Schema(description = "업태", example = "도매 및 소매업")
    private String businessCategory;

    @Schema(description = "사업장 주소", example = "서울특별시 강남구 테헤란로 123")
    private String businessAddress;

    @Schema(description = "상세주소", example = "10층 1001호")
    private String businessDetailAddress;

    @Schema(description = "이메일 (tax용)", example = "tax@example.com")
    private String taxEmail;

    @Schema(description = "사업자등록증 사본 URL", example = "https://s3.../license.jpg")
    private String businessLicenseImageUrl;

    @Schema(description = "통신판매업신고증 사본 URL", example = "https://s3.../mail_order.jpg")
    private String mailOrderLicenseImageUrl;

    @Schema(description = "통신판매업 신고번호", example = "2024-서울강남-12345")
    private String mailOrderSalesNumber;

    @Schema(description = "정산은행명", example = "국민은행")
    private String settlementBankName;

    @Schema(description = "예금주명", example = "홍길동")
    private String accountHolderName;

    @Schema(description = "계좌번호", example = "123456-78-901234")
    private String accountNumber;

    @Schema(description = "통장 사본 URL", example = "https://s3.../bankbook.jpg")
    private String bankBookImageUrl;

    @Schema(description = "신청 접수일", example = "2024-05-01T10:00:00")
    private LocalDateTime applicationDate;

    @Schema(description = "신청 처리일", example = "2024-05-02T15:30:00")
    private LocalDateTime processedDate;

    @Schema(description = "검토 메모", example = "서류 확인 완료, 통신판매업 신고번호 이상 없음")
    private String reviewMemo;
}
