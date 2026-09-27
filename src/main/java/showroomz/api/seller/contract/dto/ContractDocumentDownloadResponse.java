package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.ContractDocumentType;

import java.time.LocalDateTime;

@Schema(description = "계약 문서 다운로드 — 체결 전 생성본 또는 체결 문서")
public record ContractDocumentDownloadResponse(

        @Schema(description = "문서 종류: GENERATED_DRAFT / SIGNED_PDF / AUDIT_TRAIL", example = "SIGNED_PDF")
        ContractDocumentType documentType,

        @Schema(example = "서명 완료 계약서") String documentTypeLabel,

        @Schema(description = "다운로드 URL", example = "https://example.com/contracts/128/signed.pdf") String downloadUrl,

        @Schema(description = "원본 파일명", example = "signed-contract.pdf", nullable = true) String originalName,

        @Schema(description = "파일 크기(바이트)", example = "126384", nullable = true) Long sizeBytes,

        @Schema(description = "MIME 형식", example = "application/pdf", nullable = true) String contentType,

        @Schema(description = "문서 등록 시각(Asia/Seoul)", example = "2026-08-20T15:00:00") LocalDateTime uploadedAt
) {
}
