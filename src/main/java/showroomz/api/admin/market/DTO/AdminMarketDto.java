package showroomz.api.admin.market.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.api.seller.auth.type.SellerStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdminMarketDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 가입 신청 목록 검색 조건")
    public static class SearchCondition {
        
        @Schema(description = "판매자 상태 (NULL 또는 비워둘 시 전체 조회)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "조회 시작일 (YYYY-MM-DD)", example = "2024-01-01")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @Schema(description = "조회 종료일 (YYYY-MM-DD)", example = "2024-12-31")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "마켓 가입 신청 정보 응답")
    public static class ApplicationResponse {
        @Schema(description = "판매자 ID", example = "1")
        private Long sellerId;

        @Schema(description = "마켓 ID", example = "10")
        private Long marketId;

        @Schema(description = "이메일", example = "seller@example.com")
        private String email;

        @Schema(description = "판매자명 (대표자명)", example = "홍길동")
        private String name;

        @Schema(description = "마켓명", example = "멋쟁이 옷장")
        private String marketName;

        @Schema(description = "연락처", example = "010-1234-5678")
        private String phoneNumber;

        @Schema(description = "상태 (PENDING, APPROVED, REJECTED)", example = "PENDING")
        private SellerStatus status;

        @Schema(description = "거부 사유 (반려 시)", example = "서류 미비")
        private String rejectionReason;

        @Schema(description = "가입 신청일", example = "2024-01-01T12:00:00")
        private LocalDateTime createdAt;
    }
}
