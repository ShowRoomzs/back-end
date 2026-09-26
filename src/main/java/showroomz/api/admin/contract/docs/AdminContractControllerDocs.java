package showroomz.api.admin.contract.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.contract.dto.AdminContractDto.*;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.contract.type.*;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Contract", description = "관리자 계약 관리 API — 모두싸인에서 처리한 절차 기록 · 계약 조건은 읽기 전용")
public interface AdminContractControllerDocs {

    @Operation(summary = "계약 목록 및 조치 큐", description = "작성중 제외. queue 선택 시 tab·sort보다 우선합니다. 페이지 크기 20 또는 50.")
    PageResponse<ListItem> list(AdminContractTab tab, AdminContractQueue queue, String keyword, AdminContractSort sort,
                                PagingRequest paging);

    @Operation(summary = "검색과 무관한 큐·탭 카운트")
    Summary summary();

    @Operation(summary = "계약 상세, 권한 및 전체 이력")
    Detail detail(Long id);

    @Operation(summary = "검토 승인 및 외부 발송 시각 기록")
    ProcessResponse approve(Long id, ApproveRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "검토 반려")
    ProcessResponse reject(Long id, RejectRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "서명 현황 갱신", description = "version 필수. null로 서명 해제 가능. 기준 시각은 서버에서 기록합니다.")
    ProcessResponse signatures(Long id, SignatureRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "체결 완료", description = "서명 2종·체결 문서 2종 필수. 체결과 같은 트랜잭션에서 공구가 생성되고 응답의 groupBuyNumber로 돌려준다 — 공구 생성에 실패하면 체결도 롤백된다.")
    ProcessResponse conclude(Long id, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "대시보드 재확인 후 수동 만료")
    ProcessResponse expire(Long id, ExpireRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "계약 취소", description = "서명 요청 발송 이후 체결 전(SIGNING·CONCLUSION_PENDING)만. "
            + "이 구간은 브랜드가 취소할 수 없다. 모두싸인 서명 요청 회수 확인(signatureRequestWithdrawn) 필수 · "
            + "사유 5종 · ETC면 메모 필수. 양측에 통지합니다.")
    ProcessResponse cancel(Long id, CancelRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "외부 재발송 완료 기록", description = "미처리 요청 전부 처리. 발송 시각·서명 기한은 변경하지 않습니다.")
    ProcessResponse resend(Long id, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "체결 문서 업로드 URL 발급",
            description = """
                    서명 완료 계약서·감사 추적 인증서(PDF)를 S3에 **직접 올릴 URL**을 발급한다.

                    **권한:** ADMIN · **상태:** `CONCLUSION_PENDING`(서명 완료 후 체결 대기)일 때만

                    **업로드 흐름 — 3단계**
                    1. 이 API로 `uploadUrl`·`s3Key`를 받는다.
                    2. 파일 바이트를 `uploadUrl`로 **직접 PUT**한다. 헤더는 `Content-Type: application/pdf` **하나만** 보낸다 —
                       서명에 포함된 헤더라 값이 다르거나 다른 헤더(`x-amz-*` 등)를 더하면 S3가 403을 준다. `Authorization`도 붙이지 않는다.
                    3. `POST /v1/admin/contracts/{id}/documents`로 `s3Key`·`documentType`·`fileName`·`sizeBytes`(올린 파일의 바이트 수)를 등록한다.
                       같은 운영자가 같은 `documentType`으로 등록해야 한다.

                    **요청 필드**
                    - `documentType` **필수** — `SIGNED_PDF`(서명 완료 계약서) · `AUDIT_TRAIL`(감사 추적 인증서).
                      `GENERATED_DRAFT`는 서버가 만드는 문서라 업로드할 수 없다(400 `CONTRACT_DOCUMENT_INVALID`).
                    - `contentType` **필수** — `application/pdf` 고정. 다른 값이면 400 `CONTRACT_DOCUMENT_INVALID`.
                    - `fileName` **필수** — `.pdf`로 끝나고 200자 이하, `/`·`\\`·제어문자 불가. 저장 이름은 `{계약번호}_{fileName}`이 된다.

                    **입력 오류 응답 구분** — 둘 다 `code`가 `INVALID_INPUT`이고 어느 필드인지는 싣지 않는다.
                    - `널이어서는 안됩니다` → `documentType`이 **빠졌거나 null**이다(키 이름 오타 포함).
                    - `공백일 수 없습니다` → `contentType` 또는 `fileName`이 비었다.
                    - `입력값이 올바르지 않습니다.` → `documentType`에 없는 값을 보냈다(예: `SIGNED`).

                    `uploadUrl`은 **15분**(`expiresInSeconds` = 900) 동안 유효하다. 만료되면 이 API를 다시 부른다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발급 성공",
                    content = @Content(schema = @Schema(implementation = PresignResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "s3Key": "contracts/12/uploads/3/SIGNED_PDF/0b6f2c1e-8a4d-4c1b-9d3e-5f7a2b8c9d10.pdf",
                                      "uploadUrl": "https://{bucket}.s3.ap-northeast-2.amazonaws.com/contracts/12/uploads/3/SIGNED_PDF/0b6f2c1e-8a4d-4c1b-9d3e-5f7a2b8c9d10.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
                                      "contentType": "application/pdf",
                                      "expiresInSeconds": 900
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "입력 오류 — 필수 필드 누락·형식 위반(`INVALID_INPUT`) 또는 문서 종류·Content-Type·파일명 규칙 위반(`CONTRACT_DOCUMENT_INVALID`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "documentType 누락", value = """
                                    {"code":"INVALID_INPUT","message":"널이어서는 안됩니다"}
                                    """),
                            @ExampleObject(name = "contentType·fileName 누락", value = """
                                    {"code":"INVALID_INPUT","message":"공백일 수 없습니다"}
                                    """),
                            @ExampleObject(name = "없는 documentType 값", value = """
                                    {"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다."}
                                    """),
                            @ExampleObject(name = "GENERATED_DRAFT · PDF 아님 · 파일명 규칙 위반", value = """
                                    {"code":"CONTRACT_DOCUMENT_INVALID","message":"업로드한 PDF 문서 정보를 확인해 주세요."}
                                    """)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 정보 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "운영자가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 — 삭제됐거나 작성중(브랜드가 검토 요청 전·취소)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"CONTRACT_NOT_FOUND","message":"존재하지 않는 계약입니다."}
                                    """))),
            @ApiResponse(responseCode = "409", description = "업로드할 수 없는 상태 — 체결 대기(`CONCLUSION_PENDING`)가 아님(`CONTRACT_STATUS_CONFLICT`) · 이미 체결 완료(`CONTRACT_DOCUMENT_LOCKED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "체결 대기가 아님", value = """
                                    {"code":"CONTRACT_STATUS_CONFLICT","message":"계약 상태가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."}
                                    """),
                            @ExampleObject(name = "체결 완료", value = """
                                    {"code":"CONTRACT_DOCUMENT_LOCKED","message":"체결 완료된 계약의 문서는 변경할 수 없습니다."}
                                    """)
                    }))
    })
    PresignResponse presign(@Parameter(description = "계약 ID", example = "12") Long id,
                            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                                    content = @Content(schema = @Schema(implementation = PresignRequest.class),
                                            examples = @ExampleObject(value = """
                                                    {
                                                      "documentType": "SIGNED_PDF",
                                                      "contentType": "application/pdf",
                                                      "fileName": "서명완료_계약서.pdf"
                                                    }
                                                    """)))
                            PresignRequest request,
                            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "업로드 완료 등록 또는 교체")
    DownloadResponse register(Long id, RegisterDocumentRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "체결 전 문서 삭제")
    ResponseEntity<Void> delete(Long id, ContractDocumentType type, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "문서 다운로드 URL", description = "유효기간 5분.")
    DownloadResponse download(Long id, ContractDocumentType type);

    @Operation(summary = "계약서 PDF 생성본 다운로드", description = "제출본 시각 기준 캐시. 재요청 시 재생성. 미확정 문안은 초안 표시를 유지합니다.")
    DownloadResponse draft(Long id, @Parameter(hidden = true) UserPrincipal principal);
}
