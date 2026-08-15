package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;
import showroomz.domain.terms.type.TermsVersionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "버전 상세 (기획 §21-4) — 조회 전용. 수정·삭제 액션이 없다. "
        + "시행중 버전도 같은 응답을 쓰며 상태 배지만 다르다.")
public class AdminTermsVersionDetailResponse {

    @Schema(description = "문서 ID", example = "1")
    private Long documentId;

    @Schema(description = "문서명", example = "소비자 이용약관")
    private String documentName;

    @Schema(description = "유형", example = "TERMS_OF_SERVICE")
    private TermsType type;

    @Schema(description = "유형 표시명", example = "이용 약관")
    private String typeName;

    @Schema(description = "대상", example = "USER")
    private TermsTarget target;

    @Schema(description = "대상 표시명", example = "소비자")
    private String targetName;

    @Schema(description = "버전 ID", example = "11")
    private Long versionId;

    @Schema(description = "버전 번호 (접두 v 없음)", example = "3.0")
    private String versionNumber;

    @Schema(description = "버전 (화면 표기)", example = "v3.0")
    private String version;

    @Schema(description = "버전 상태", example = "PAST")
    private TermsVersionStatus status;

    @Schema(description = "상태 표시명", example = "과거 버전")
    private String statusName;

    @Schema(description = "시행 기간 시작 = 시행일", example = "2026-04-15")
    private LocalDate effectiveStartDate;

    @Schema(description = "시행 기간 종료 — 다음 버전 시행일의 하루 전. 시행중·시행 예정이면 비어 있다", example = "2026-05-31")
    private LocalDate effectiveEndDate;

    @Schema(description = "본문 — 조회 전용", example = "제1조(목적) ...")
    private String content;

    @Schema(description = "등록자 (운영자)", example = "김운영")
    private String registrantName;

    @Schema(description = "등록일시", example = "2026-04-01T10:04:00")
    private LocalDateTime registeredAt;

    @Schema(description = "이 버전을 대체한 다음 버전 (화면 표기) — 없으면 비어 있다", example = "v3.1")
    private String nextVersion;

    @Schema(description = "교체일 = 다음 버전의 시행일 — 없으면 비어 있다", example = "2026-06-01")
    private LocalDate replacedAt;

    @Schema(description = "‹ 이전 (더 오래된 버전) ID — 없으면 비어 있다", example = "10")
    private Long previousVersionId;

    @Schema(description = "다음 › (더 최신 버전) ID — 없으면 비어 있다", example = "12")
    private Long nextVersionId;
}
