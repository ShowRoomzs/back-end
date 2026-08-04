package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "관리자용 판매자 상세 검토 정보 응답")
public class AdminSellerDetailResponse {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "입점 신청 처리 이력 항목")
    public static class ProcessingHistoryItem {

        @Schema(description = "처리 유형", example = "APPLICATION_RECEIVED",
                allowableValues = {"APPLICATION_RECEIVED", "APPLICATION_APPROVED", "APPLICATION_REJECTED"})
        private String type;

        @Schema(description = "처리 내용", example = "신청 접수")
        private String label;

        @Schema(description = "처리 일시", example = "2026-07-10T14:22:00")
        private LocalDateTime processedAt;
    }

    @Schema(description = "입점 신청서 ID", example = "100")
    private Long applicationId;

    @Schema(description = "판매자 ID", example = "1")
    private Long sellerId;

    @Schema(description = "판매자 계정 이메일", example = "seller@example.com")
    private String email;

    @Schema(description = "마켓명", example = "쇼룸즈")
    private String marketName;

    @Schema(description = "고객센터 전화", example = "02-1234-5678")
    private String csNumber;

    @Schema(description = "판매 담당자", example = "김판매")
    private String sellerName;

    @Schema(description = "판매자 승인 상태", example = "PENDING")
    private String status;

    @Schema(description = "반려 사유 타입 (반려 시, enum 이름)", example = "INSUFFICIENT_DOCUMENTS")
    private String rejectionReason;

    @Schema(description = "반려 상세 사유 (반려 시, 선택)", example = "사업자 등록증이 흐릿합니다.")
    private String rejectionReasonDetail;

    @Schema(description = "사업자 구분", example = "개인사업자")
    private String businessType;

    @Schema(description = "대표자명", example = "홍길동")
    private String representativeName;

    @Schema(description = "대표자 연락처", example = "010-1234-5678")
    private String representativeContact;

    @Schema(description = "사업자등록증 상호명", example = "(주)쇼룸즈")
    private String businessCompanyName;

    @Schema(description = "사업자 등록번호 (반려 시 null/파기)", example = "123-45-67890")
    private String businessRegistrationNumber;

    @Schema(description = "사업자등록번호 일방향 해시 (반려 시에만 반환, 복원 불가)", example = "a3f9c210...")
    private String businessRegistrationNumberHash;

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

    @Schema(description = "정산은행명 (가입 시 bankCode로 조회한 은행명)", example = "KB국민은행")
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

    @Schema(description = "처리자(운영자) 로그인 아이디(이메일) — 승인/반려 시에만 반환", example = "admin@showroomz.com")
    private String processorLoginId;

    @Schema(description = "처리 이력 (신청 접수, 신청 승인/반려 등 시간순)")
    private List<ProcessingHistoryItem> processingHistory;

    @Schema(description = "검토 메모", example = "서류 확인 완료, 통신판매업 신고번호 이상 없음")
    private String reviewMemo;
}
