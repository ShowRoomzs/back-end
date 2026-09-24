package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.ContractDocumentType;

import java.time.LocalDateTime;

@Schema(description = "계약 문서 다운로드 — 체결 전 생성본 또는 체결 문서")
public record ContractDocumentDownloadResponse(

        ContractDocumentType documentType,

        @Schema(example = "서명 완료 계약서") String documentTypeLabel,

        @Schema(description = "다운로드 URL") String downloadUrl,

        @Schema(nullable = true) String originalName,

        @Schema(nullable = true) Long sizeBytes,

        @Schema(nullable = true) String contentType,

        LocalDateTime uploadedAt
) {
}
