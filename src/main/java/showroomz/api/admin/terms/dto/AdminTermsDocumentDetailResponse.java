package showroomz.api.admin.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.terms.type.TermsDocumentStatus;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@Schema(description = "문서 상세 (기획 §21-4) — 문서 정보 · 시행 원문(조회 전용) · 버전 이력 · 우측 현재 시행 카드")
public class AdminTermsDocumentDetailResponse {

    @Schema(description = "문서 ID", example = "1")
    private Long documentId;

    @Schema(description = "문서명", example = "소비자 이용약관")
    private String name;

    @Schema(description = "유형 — 문서 속성이라 조회만 한다", example = "TERMS_OF_SERVICE")
    private TermsType type;

    @Schema(description = "유형 표시명", example = "이용 약관")
    private String typeName;

    @Schema(description = "대상 — 문서 속성이라 조회만 한다", example = "USER")
    private TermsTarget target;

    @Schema(description = "대상 표시명", example = "소비자")
    private String targetName;

    @Schema(description = "문서 상태 — 시행중 / 시행 예정 / 구버전", example = "EFFECTIVE")
    private TermsDocumentStatus status;

    @Schema(description = "상태 표시명", example = "시행중")
    private String statusName;

    @Schema(description = "표시 버전 번호 (시행중 버전 · 없으면 시행 예정 버전)", example = "3.1")
    private String versionNumber;

    @Schema(description = "표시 버전 (화면 표기)", example = "v3.1")
    private String version;

    @Schema(description = "표시 버전의 시행일", example = "2026-06-01")
    private LocalDate effectiveDate;

    @Schema(description = "표시 버전의 등록자", example = "김운영")
    private String registrantName;

    @Schema(description = "시행 원문 — 표시 버전의 본문. 조회 전용이며 수정 API가 없다")
    private String content;

    @Schema(description = "보관 중인 과거 버전 수 — 동의 기록이 참조하므로 삭제하지 않는다", example = "2")
    private long pastVersionCount;

    @Schema(description = "버전 이력 (시행일 최신순) — 행 클릭 시 버전 상세로 이동한다")
    private List<AdminTermsVersionHistoryResponse> versions;

    @Schema(description = "`새 버전 등록` 노출 여부 — 구버전 문서는 false", example = "true")
    private boolean canRegisterNewVersion;
}
