package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.type.TermsDocumentStatus;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.time.LocalDate;

@Getter
@Builder
@Schema(description = "문서 목록 행 (기획 §21-3) — 컬럼 6종: 문서명 · 대상 · 버전 · 상태 · 시행일 · 관리")
public class AdminTermsListResponse {

    @Schema(description = "문서 ID — 행 클릭 시 문서 상세로 이동한다", example = "1")
    private Long documentId;

    @Schema(description = "문서명", example = "소비자 이용약관")
    private String name;

    @Schema(description = "유형", example = "TERMS_OF_SERVICE")
    private TermsType type;

    @Schema(description = "유형 표시명", example = "이용 약관")
    private String typeName;

    @Schema(description = "대상", example = "USER")
    private TermsTarget target;

    @Schema(description = "대상 표시명", example = "소비자")
    private String targetName;

    @Schema(description = "표시 버전 번호 (접두 v 없음)", example = "3.1")
    private String versionNumber;

    @Schema(description = "표시 버전 (화면 표기)", example = "v3.1")
    private String version;

    @Schema(description = "행 상태 — 시행중 / 시행 예정 / 구버전", example = "EFFECTIVE")
    private TermsDocumentStatus status;

    @Schema(description = "상태 표시명", example = "시행중")
    private String statusName;

    @Schema(description = "표시 버전의 시행일", example = "2026-06-01")
    private LocalDate effectiveDate;

    @Schema(description = "관리 열의 `새 버전 등록` 노출 여부 — 구버전 행은 false(관리 열을 비운다)", example = "true")
    private boolean canRegisterNewVersion;

    public static AdminTermsListResponse of(TermsDocument document, TermsVersion displayVersion,
                                            TermsDocumentStatus status) {
        return AdminTermsListResponse.builder()
                .documentId(document.getId())
                .name(document.getName())
                .type(document.getType())
                .typeName(document.getType().getDisplayName())
                .target(document.getTarget())
                .targetName(document.getTarget().getDisplayName())
                .versionNumber(displayVersion == null ? null : displayVersion.getVersionNumber())
                .version(displayVersion == null ? null : displayVersion.getDisplayVersion())
                .status(status)
                .statusName(status == null ? null : status.getDisplayName())
                .effectiveDate(displayVersion == null ? null : displayVersion.getEffectiveDate())
                .canRegisterNewVersion(!document.isSuperseded())
                .build();
    }
}
