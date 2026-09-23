package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.ContractDocumentType;

import java.time.LocalDateTime;

/** 체결 문서 다운로드 — 체결완료 계약에서만 존재한다. */
@Schema(description = "체결 문서 다운로드")
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
