package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.admin.market.type.RejectionReasonType;

import java.time.LocalDateTime;

public class SellerDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "이메일 중복 확인 응답")
    public static class CheckEmailResponse {
        @Schema(description = "사용 가능 여부", example = "true")
        private boolean isAvailable;
        @Schema(description = "응답 코드", example = "AVAILABLE")
        private String code;
        @Schema(description = "메시지", example = "사용 가능한 이메일입니다.")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "셀러 상태 업데이트 요청 DTO")
    public static class UpdateStatusRequest {
        @Schema(description = "승인 상태 (APPROVED: 승인, REJECTED: 반려)", example = "APPROVED")
        private String status;

        @Schema(description = "반려 사유 타입 (status가 REJECTED일 때 필수)", example = "INSUFFICIENT_DOCUMENTS")
        private RejectionReasonType rejectionReasonType;

        @Schema(description = "상세 사유 (상태와 무관하게 선택적으로 입력, 반려 시 항상 저장됨)", example = "추가적인 상세 사유입니다.")
        private String rejectionReasonDetail;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "가입 대기 판매자 정보")
    public static class PendingSellerResponse {
        @Schema(description = "판매자 ID", example = "1")
        private Long sellerId;

        @Schema(description = "이메일", example = "seller@example.com")
        private String email;

        @Schema(description = "판매자명 (대표자명)", example = "홍길동")
        private String name;

        @Schema(description = "마켓명", example = "멋쟁이 옷장")
        private String marketName;

        @Schema(description = "연락처", example = "010-1234-5678")
        private String phoneNumber;

        @Schema(description = "가입 신청일", example = "2024-01-01T12:00:00")
        private LocalDateTime createdAt;
    }
}

