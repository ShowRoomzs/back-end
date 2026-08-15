package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "버전 이력 행 (기획 §21-4) — 버전 · 시행일 · 등록자 · 등록일시 · 상태. 행 클릭 → 버전 상세")
public class AdminTermsVersionHistoryResponse {

    @Schema(description = "버전 ID", example = "12")
    private Long versionId;

    @Schema(description = "버전 번호 (접두 v 없음)", example = "3.1")
    private String versionNumber;

    @Schema(description = "버전 (화면 표기)", example = "v3.1")
    private String version;

    @Schema(description = "시행일", example = "2026-06-01")
    private LocalDate effectiveDate;

    @Schema(description = "등록자 (운영자)", example = "김운영")
    private String registrantName;

    @Schema(description = "등록일시", example = "2026-05-18T15:20:00")
    private LocalDateTime registeredAt;

    @Schema(description = "버전 상태 — 시행중 / 시행 예정 / 과거 버전", example = "EFFECTIVE")
    private TermsVersionStatus status;

    @Schema(description = "상태 표시명", example = "시행중")
    private String statusName;

    public static AdminTermsVersionHistoryResponse of(TermsVersion version, String registrantName) {
        return AdminTermsVersionHistoryResponse.builder()
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .version(version.getDisplayVersion())
                .effectiveDate(version.getEffectiveDate())
                .registrantName(registrantName)
                .registeredAt(version.getCreatedAt())
                .status(version.getStatus())
                .statusName(version.getStatus().getDisplayName())
                .build();
    }
}
