package showroomz.api.app.terms.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.terms.dto.TermsDocumentDetailResponse;
import showroomz.api.app.terms.dto.TermsDocumentResponse;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.util.List;

@Tag(name = "Common - Terms", description = "공용 약관·정책 문서 API (기획 §21 · C18 문서 뷰어)\n\n")
public interface TermsControllerDocs {

    @Operation(
            summary = "시행 중인 약관·정책 문서 목록",
            description = "문서 뷰어·회원가입 동의 화면이 쓰는 목록입니다.\n\n"
                    + "**동작 방식:**\n"
                    + "- **시행 중인 버전이 있는 문서만** 내려갑니다. 시행 예정 버전은 시행일 00:00에 서버 배치가 "
                    + "전환하기 전까지 노출되지 않습니다.\n"
                    + "- 정렬: 유형 순(이용 약관 → 개인정보처리방침 → 마케팅 동의) → 등록 순\n"
                    + "- 본문은 내려가지 않습니다 — 원문은 상세에서 받습니다.\n\n"
                    + "**대상 필터(`target`):** 지정하면 **해당 대상 + 전체(ALL) 대상** 문서를 함께 내려줍니다 — "
                    + "소비자에게는 '소비자 약관'과 '전체 대상 개인정보처리방침'이 함께 필요합니다.\n\n"
                    + "**권한:** 비회원/회원 모두 조회 가능 (인증 불필요)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TermsDocumentResponse.class),
                            examples = @ExampleObject(
                                    name = "소비자 대상 문서 목록",
                                    value = "[\n"
                                            + "  {\n"
                                            + "    \"documentId\": 1,\n"
                                            + "    \"name\": \"소비자 이용약관\",\n"
                                            + "    \"type\": \"TERMS_OF_SERVICE\",\n"
                                            + "    \"typeName\": \"이용 약관\",\n"
                                            + "    \"target\": \"USER\",\n"
                                            + "    \"targetName\": \"소비자\",\n"
                                            + "    \"version\": \"v3.1\",\n"
                                            + "    \"effectiveDate\": \"2026-06-01\"\n"
                                            + "  }\n"
                                            + "]"
                            )
                    )
            )
    })
    ResponseEntity<List<TermsDocumentResponse>> getTerms(
            @Parameter(description = "유형 필터 (미입력 시 전체)", example = "TERMS_OF_SERVICE")
            TermsType type,
            @Parameter(description = "대상 필터 — 지정 시 해당 대상 + 전체(ALL) 대상 문서를 함께 조회", example = "USER")
            TermsTarget target
    );

    @Operation(
            summary = "약관·정책 원문 조회",
            description = "문서 뷰어 본문입니다. 시행 중인 버전의 원문과 **시행일·버전**을 함께 내려줍니다 — "
                    + "뷰어 상단에 '시행일 · 버전'을 고정 표기해 어느 시점 문서인지 분명히 합니다.\n\n"
                    + "시행 중인 버전이 없는 문서(시행 예정만 있거나 구버전)는 404로 응답합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 시행 중이 아닌 문서",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "시행 중이 아닌 문서",
                                    value = "{\n"
                                            + "  \"code\": \"NOT_FOUND_DATA\",\n"
                                            + "  \"message\": \"시행 중인 문서가 아닙니다.\"\n"
                                            + "}"
                            )
                    )
            )
    })
    ResponseEntity<TermsDocumentDetailResponse> getTermsDetail(
            @Parameter(description = "문서 ID", required = true) Long documentId
    );
}
