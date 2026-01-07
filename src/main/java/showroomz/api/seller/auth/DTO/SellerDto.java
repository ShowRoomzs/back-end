package showroomz.api.seller.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Schema(description = "판매자 계정 상태 변경 요청")
    public static class UpdateStatusRequest {
        @Schema(description = "변경할 상태 (APPROVED: 승인, REJECTED: 반려)", example = "APPROVED")
        private String status;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "가입 대기 판매자 정보")
    public static class PendingSellerResponse {
        @Schema(description = "관리자(판매자) ID", example = "1")
        private Long adminId;

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

