package showroomz.api.app.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.time.LocalDate;

/**
 * 문서 뷰어 본문 (기획 C18 문서 뷰어) — 시행 중인 버전의 원문만 내려간다.
 *
 * <p>시행 예정 버전은 시행일 00:00 전까지 노출되지 않으며, 과거 버전도 소비자 화면에는 내려가지 않는다.
 */
@Getter
@Schema(description = "시행 중인 약관·정책 원문")
public class TermsDocumentDetailResponse {

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

    @Schema(description = "시행일", example = "2026-06-01")
    private final LocalDate effectiveDate;

    @Schema(description = "원문", example = "제1조 (목적) ...")
    private final String content;

    public TermsDocumentDetailResponse(TermsVersion version) {
        TermsDocument document = version.getDocument();
        this.documentId = document.getId();
        this.name = document.getName();
        this.type = document.getType();
        this.typeName = document.getType().getDisplayName();
        this.target = document.getTarget();
        this.targetName = document.getTarget().getDisplayName();
        this.version = version.getDisplayVersion();
        this.effectiveDate = version.getEffectiveDate();
        this.content = version.getContent();
    }

    public static TermsDocumentDetailResponse from(TermsVersion version) {
        return new TermsDocumentDetailResponse(version);
    }
}
