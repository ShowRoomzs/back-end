package showroomz.api.app.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.time.LocalDate;

/** 문서 뷰어 목록 행 (기획 C18 문서 뷰어) — 본문은 상세에서 받는다. */
@Getter
@Schema(description = "시행 중인 약관·정책 문서")
public class TermsDocumentResponse {

    @Schema(description = "문서 ID", example = "1")
    private final Long documentId;

    @Schema(description = "문서명", example = "소비자 이용약관")
    private final String name;

    @Schema(description = "유형", example = "TERMS_OF_SERVICE")
    private final TermsType type;

    @Schema(description = "유형 표시명", example = "이용 약관")
    private final String typeName;

    @Schema(description = "대상", example = "USER")
    private final TermsTarget target;

    @Schema(description = "대상 표시명", example = "소비자")
    private final String targetName;

    @Schema(description = "버전 (화면 표기)", example = "v3.1")
    private final String version;

    @Schema(description = "시행일 — 뷰어 상단에 고정 표기한다", example = "2026-06-01")
    private final LocalDate effectiveDate;

    public TermsDocumentResponse(TermsVersion version) {
        TermsDocument document = version.getDocument();
        this.documentId = document.getId();
        this.name = document.getName();
        this.type = document.getType();
        this.typeName = document.getType().getDisplayName();
        this.target = document.getTarget();
        this.targetName = document.getTarget().getDisplayName();
        this.version = version.getDisplayVersion();
        this.effectiveDate = version.getEffectiveDate();
    }

    public static TermsDocumentResponse from(TermsVersion version) {
        return new TermsDocumentResponse(version);
    }
}
