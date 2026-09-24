package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.ContractDocumentType;

import java.time.LocalDateTime;

/** 계약 문서 다운로드 — 체결 전에는 계약서 생성본, 체결완료에서는 체결 문서 2종이다. */
@Schema(description = "계약 문서 다운로드 — 체결 전 생성본 또는 체결 문서")
public record CreatorContractDocumentDownloadResponse(

        ContractDocumentType documentType,

        @Schema(example = "서명 완료 계약서") String documentTypeLabel,

        @Schema(description = "다운로드 URL") String downloadUrl,

        @Schema(nullable = true) String originalName,

        @Schema(nullable = true) Long sizeBytes,

        @Schema(nullable = true) String contentType,

        LocalDateTime uploadedAt
) {
}
