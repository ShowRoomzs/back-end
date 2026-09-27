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

    @Operation(summary = "계약 목록 및 조치 큐", description = """
            작성중(DRAFT)·삭제된 계약은 제외합니다. `keyword`는 계약명·계약번호·브랜드명·쇼룸명에 부분 일치 검색합니다.
            `queue`를 선택하면 `tab`과 `sort`보다 우선하고 큐별 고정 정렬을 적용합니다.

            - `REVIEW`: 검토 대기, 검토 요청이 오래된 순
            - `CONCLUSION`: 체결 처리 대기, 서명 확인 기준 시각이 오래된 순
            - `EXPIRY`: 서명 기한이 지난 서명 진행중 계약, 기한이 오래된 순
            - `RESEND`: 미처리 재발송 요청이 있는 서명 진행중 계약, 최초 미처리 요청이 오래된 순

            큐가 없으면 `tab`(기본 `ALL`)과 `sort`(기본 `REVIEW_REQUESTED_ASC`)를 적용합니다.
            `CLOSED` 탭은 `DECLINED`·`EXPIRED`·`CANCELED`를 포함합니다. `page`는 1부터 시작하고 `size`는 20 또는 50입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = """
                                    {"content":[{"contractId":12,"contractNumber":"CTR-20260920-012","title":"가을 공구 계약","brandName":"글로우랩","creatorName":"하윤","itemCount":2,"startAt":"2026-10-01T00:00:00","endAt":"2026-10-07T23:59:59","reviewRequestedAt":"2026-09-20T10:00:00","status":"REVIEW_PENDING","statusLabel":"검토 대기","statusTone":"INFO"}],"pageInfo":{"currentPage":1,"totalPages":1,"totalResults":1,"limit":20,"hasNext":false}}
                                    """))),
            @ApiResponse(responseCode = "400", description = "잘못된 탭·큐·정렬값 또는 size(20·50 이외)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PageResponse<ListItem> list(
            @Parameter(description = "상태 탭. 기본 ALL", example = "ALL") AdminContractTab tab,
            @Parameter(description = "조치 큐. 지정 시 tab·sort 무시", example = "REVIEW") AdminContractQueue queue,
            @Parameter(description = "계약명·번호·브랜드명·쇼룸명 부분 검색", example = "글로우랩") String keyword,
            @Parameter(description = "일반 목록 정렬. 기본 REVIEW_REQUESTED_ASC", example = "REVIEW_REQUESTED_ASC") AdminContractSort sort,
            PagingRequest paging);

    @Operation(summary = "조치 큐·상태 탭 건수", description = "검색어와 무관한 전체 계약 기준입니다. `actionRequiredCount`는 네 조치 큐의 건수를 합한 값이며, 같은 계약이 여러 큐에 속하면 각각 셉니다. 작성중·삭제된 계약은 제외합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "큐·탭별 건수",
                    content = @Content(schema = @Schema(implementation = Summary.class), examples = @ExampleObject(value = """
                            {"queues":{"REVIEW":3,"CONCLUSION":2,"EXPIRY":1,"RESEND":1},"tabCounts":{"ALL":18,"REVIEW_PENDING":3,"SIGNING":5,"CONCLUSION_PENDING":2,"CONCLUDED":4,"CLOSED":4},"actionRequiredCount":7}
                            """))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Summary summary();

    @Operation(summary = "계약 상세, 권한 및 전체 이력", description = """
            계약 조건은 읽기 전용입니다. `permissions`의 각 플래그로 현재 가능한 조치를 표시합니다.
            `documents`는 `GENERATED_DRAFT`·`SIGNED_PDF`·`AUDIT_TRAIL` 세 종류를 모두 반환하며, 미등록 문서는 `exists=false`이고 파일 정보가 null입니다.
            문서 `downloadUrl`은 임시 URL입니다. `history`는 발생 시각·ID 오름차순입니다.
            `cancelRequest`는 운영자가 취소한 계약에서만 반환하며, 기록 도입 전 취소 건은 내부 필드가 null일 수 있습니다.
            체결 전 `groupBuy`의 ID·번호·상태는 null입니다. 서명 수정 시에는 상세의 `version`을 요청에 그대로 전달합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "계약 상세",
                    content = @Content(schema = @Schema(implementation = Detail.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음·작성중·삭제됨 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Detail detail(@Parameter(description = "계약 ID", example = "12") Long id);

    @Operation(summary = "검토 승인 및 외부 서명 요청 기록", description = """
            `REVIEW_PENDING` 계약에서 모두싸인 수신자 등록·문서 업로드·서명 요청 발송을 마친 뒤 호출합니다.
            체크리스트 3개가 모두 `true`여야 합니다. `signatureRequestedAt`은 검토 요청 시각 이후·현재 이전이어야 하고,
            `signatureDeadlineAt`은 발송 시각보다 늦어야 합니다. 성공하면 `SIGNING`으로 바뀌고 양측에 알립니다.
            시각은 서버에서 발송하는 값이 아니라 운영자가 외부 발송 결과를 입력한 값입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 및 서명 진행중 전환", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"SIGNING","version":2,"groupBuyNumber":null}
                    """))),
            @ApiResponse(responseCode = "400", description = "체크리스트 미확인 (`CONTRACT_CHECKLIST_REQUIRED`) 또는 발송·기한 시각 오류 (`CONTRACT_SIGNATURE_TIME_INVALID`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검토 대기 상태가 아님 (`CONTRACT_REVIEW_NOT_PENDING`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse approve(@Parameter(description = "계약 ID", example = "12") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = ApproveRequest.class), examples = @ExampleObject(value = """
                    {"recipientsRegistered":true,"documentUploaded":true,"requestSent":true,"signatureRequestedAt":"2026-09-21T09:50:00","signatureDeadlineAt":"2026-09-28T23:59:59"}
                    """))) ApproveRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "검토 반려", description = "`REVIEW_PENDING` 계약만 반려할 수 있습니다. `reasonCode`는 필수이고 `reasonDetail`은 빈 문자열이 아닌 1000자 이하의 설명이어야 합니다. 반려 후 `REVIEW_REJECTED`가 되고 브랜드에 알립니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"REVIEW_REJECTED","version":2,"groupBuyNumber":null}
                    """))),
            @ApiResponse(responseCode = "400", description = "사유 누락·길이 오류 (`INVALID_INPUT`, `CONTRACT_REJECT_DETAIL_REQUIRED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "검토 대기 상태가 아님 (`CONTRACT_REVIEW_NOT_PENDING`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse reject(@Parameter(description = "계약 ID", example = "12") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = RejectRequest.class), examples = @ExampleObject(value = """
                    {"reasonCode":"INFO_MISMATCH","reasonDetail":"사업자 정보가 계약서와 다릅니다."}
                    """))) RejectRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "서명 현황 갱신", description = """
            `SIGNING`·`CONCLUSION_PENDING`에서 사용합니다. 양측 서명 시각을 **모두** 보내며, 아직 서명하지 않은 쪽은 `null`로 보냅니다.
            기록된 서명을 취소할 때도 해당 값을 `null`로 보냅니다. 서명 시각은 발송 시각 이후·현재 이전이어야 합니다.
            상세 조회에서 받은 `version`을 전달해야 하며 일치하지 않으면 충돌입니다. 양측 서명이 있으면 `CONCLUSION_PENDING`,
            하나라도 없으면 `SIGNING`이 됩니다. 수정 기준 시각은 서버가 기록합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "서명 현황 및 상태 갱신", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"CONCLUSION_PENDING","version":3,"groupBuyNumber":null}
                    """))),
            @ApiResponse(responseCode = "400", description = "서명 시각 오류 (`CONTRACT_SIGNATURE_TIME_INVALID`) 또는 version 누락·음수 (`INVALID_INPUT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "version 불일치 (`CONTRACT_MODIFIED_ELSEWHERE`), 상태 불일치 (`CONTRACT_STATUS_CONFLICT`) 또는 체결 완료 (`CONTRACT_SIGNATURE_UPDATE_LOCKED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse signatures(@Parameter(description = "계약 ID", example = "12") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = SignatureRequest.class), examples = @ExampleObject(value = """
                    {"brandSignedAt":"2026-09-22T11:00:00","creatorSignedAt":"2026-09-22T12:00:00","version":2}
                    """))) SignatureRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "체결 완료 및 공구 생성", description = "`CONCLUSION_PENDING`에서 양측 서명 시각과 `SIGNED_PDF`·`AUDIT_TRAIL` 문서가 모두 등록된 경우에만 처리합니다. 체결과 공구 생성은 같은 트랜잭션으로 처리합니다. 성공 응답의 `groupBuyNumber`는 생성된 공구 번호이며, 생성 실패 시 체결도 취소됩니다. 양측에 체결을 알립니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "체결 완료와 생성된 공구 번호", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"CONCLUDED","version":4,"groupBuyNumber":"GB-20260927-001"}
                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "상태·서명 미충족 (`CONTRACT_STATUS_CONFLICT`), 문서 부족 (`CONTRACT_DOCUMENT_REQUIRED`), 공구 중복 (`CONTRACT_GROUP_BUY_ALREADY_CREATED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse conclude(@Parameter(description = "계약 ID", example = "12") Long id, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "대시보드 재확인 후 수동 만료", description = "`SIGNING` 계약의 서명 기한이 현재 시각보다 지났을 때만 사용합니다. 모두싸인 대시보드를 다시 확인하고 `dashboardRechecked=true`를 보냅니다. 성공하면 `EXPIRED`가 되고 양측에 알립니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "만료 완료", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"EXPIRED","version":3,"groupBuyNumber":null}
                    """))),
            @ApiResponse(responseCode = "400", description = "대시보드 재확인 미완료 (`CONTRACT_CHECKLIST_REQUIRED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "상태 불일치 (`CONTRACT_STATUS_CONFLICT`) 또는 기한 미도래 (`CONTRACT_EXPIRE_NOT_DUE`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse expire(@Parameter(description = "계약 ID", example = "12") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = ExpireRequest.class), examples = @ExampleObject(value = """
                    {"dashboardRechecked":true}
                    """))) ExpireRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "계약 취소", description = "서명 요청 발송 이후 체결 전(SIGNING·CONCLUSION_PENDING)만. "
            + "이 구간은 브랜드가 취소할 수 없다. 모두싸인 서명 요청 회수 확인(signatureRequestWithdrawn) 필수 · "
            + "사유 5종 · ETC면 메모 필수. 양측에 통지합니다. "
            + "요청자(requesterType) 필수 — SELLER·CREATOR면 요청 경로(requestChannel)·요청 시각(requestedAt)도 필수(현재 이전 · 계약 생성 이후), "
            + "ADMIN(직권)이면 둘 다 보내지 않는다. 위반은 400 CONTRACT_CANCEL_REQUESTER_INVALID(어느 값인지는 message). "
            + "기록한 요청자는 어드민 상세 cancelRequest에만 나가고 브랜드·스튜디오에는 보이지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 완료", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"CANCELED","version":4,"groupBuyNumber":null}
                    """))),
            @ApiResponse(responseCode = "400", description = "회수 확인 누락 (`CONTRACT_CHECKLIST_REQUIRED`), 기타 사유의 메모 누락 (`CONTRACT_CANCEL_REASON_MEMO_REQUIRED`), 요청자·경로·시각 오류 (`CONTRACT_CANCEL_REQUESTER_INVALID`) 또는 입력 형식 오류 (`INVALID_INPUT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "요청자 정보 오류", value = """
                            {"code":"CONTRACT_CANCEL_REQUESTER_INVALID","message":"요청 경로와 요청 시각을 입력해 주세요."}
                            """),
                    @ExampleObject(name = "기타 사유 메모 누락", value = """
                            {"code":"CONTRACT_CANCEL_REASON_MEMO_REQUIRED","message":"기타 사유는 상세 설명이 필요합니다."}
                            """)})),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "취소 가능 상태가 아님 (`CONTRACT_STATUS_CONFLICT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse cancel(@Parameter(description = "계약 ID", example = "12") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = CancelRequest.class), examples = {
                    @ExampleObject(name = "브랜드 요청", value = """
                            {"signatureRequestWithdrawn":true,"reasonCode":"SCHEDULE_CHANGE","memo":"일정 조정 요청","requesterType":"SELLER","requestChannel":"THREAD","requestedAt":"2026-09-22T10:05:00"}
                            """),
                    @ExampleObject(name = "운영자 직권", value = """
                            {"signatureRequestWithdrawn":true,"reasonCode":"ETC","memo":"운영 정책에 따른 취소","requesterType":"ADMIN"}
                            """)
            })) CancelRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "외부 재발송 완료 기록", description = "미처리 요청 전부 처리. 발송 시각·서명 기한은 변경하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "미처리 재발송 요청 처리 기록", content = @Content(schema = @Schema(implementation = ProcessResponse.class), examples = @ExampleObject(value = """
                    {"contractId":12,"status":"SIGNING","version":3,"groupBuyNumber":null}
                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "서명 진행중이 아님 (`CONTRACT_STATUS_CONFLICT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ProcessResponse resend(@Parameter(description = "계약 ID", example = "12") Long id, @Parameter(hidden = true) UserPrincipal principal);

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
            @ApiResponse(responseCode = "404", description = "계약 없음·작성중·삭제됨 (`CONTRACT_NOT_FOUND`)",
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

    @Operation(summary = "업로드 완료 등록 또는 교체", description = """
            먼저 presign API로 URL을 받고 S3에 PDF를 PUT한 뒤 호출합니다. `s3Key`는 같은 운영자·계약·문서 종류로 발급된 키여야 합니다.
            `sizeBytes`는 실제 업로드한 바이트 수와 같아야 하며 최소 5바이트입니다. `fileName`은 `.pdf`로 끝나는 200자 이하의 원본 이름입니다.
            `CONCLUSION_PENDING`에서 `SIGNED_PDF`·`AUDIT_TRAIL`만 등록할 수 있습니다. 같은 종류가 이미 있으면 교체합니다.
            성공 응답은 새 문서의 임시 다운로드 URL이며, 이력에 업로드 또는 교체가 남습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록·교체한 문서의 다운로드 정보", content = @Content(schema = @Schema(implementation = DownloadResponse.class))),
            @ApiResponse(responseCode = "400", description = "필수값·크기·파일명·s3Key 규칙 오류 또는 업로드 객체 없음 (`INVALID_INPUT`, `CONTRACT_DOCUMENT_INVALID`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "체결 대기 상태가 아님 (`CONTRACT_STATUS_CONFLICT`) 또는 체결 완료 (`CONTRACT_DOCUMENT_LOCKED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    DownloadResponse register(@Parameter(description = "계약 ID", example = "12") Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = RegisterDocumentRequest.class), examples = @ExampleObject(value = """
                    {"documentType":"SIGNED_PDF","s3Key":"contracts/12/uploads/3/SIGNED_PDF/0b6f2c1e-8a4d-4c1b-9d3e-5f7a2b8c9d10.pdf","fileName":"서명완료_계약서.pdf","sizeBytes":1258291}
                    """))) RegisterDocumentRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "체결 전 문서 삭제", description = "`CONCLUSION_PENDING`에서 등록된 `SIGNED_PDF`·`AUDIT_TRAIL` 문서만 삭제합니다. `GENERATED_DRAFT`는 서버 생성 문서라 삭제할 수 없습니다. 성공 시 문서 삭제 이력을 기록하고 본문 없이 204를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "문서 삭제 완료"),
            @ApiResponse(responseCode = "400", description = "문서 종류 오류 (`CONTRACT_DOCUMENT_INVALID`, `INVALID_INPUT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 또는 문서 없음 (`CONTRACT_NOT_FOUND`, `CONTRACT_DOCUMENT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "체결 대기 상태가 아님 (`CONTRACT_STATUS_CONFLICT`) 또는 체결 완료 (`CONTRACT_DOCUMENT_LOCKED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> delete(@Parameter(description = "계약 ID", example = "12") Long id,
            @Parameter(description = "삭제할 문서 종류", example = "SIGNED_PDF") ContractDocumentType type,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "문서 다운로드 URL", description = "등록된 문서의 5분 유효 임시 다운로드 URL을 반환합니다. `GENERATED_DRAFT`는 현재 검토 요청 시각과 일치하는 생성본만 제공합니다. 만료된 URL은 다시 요청합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문서 다운로드 정보", content = @Content(schema = @Schema(implementation = DownloadResponse.class), examples = @ExampleObject(value = """
                    {"downloadUrl":"https://example-bucket.s3.ap-northeast-2.amazonaws.com/contracts/12/documents/signed.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&...","fileName":"CTR-20260920-012_서명완료_계약서.pdf","sizeBytes":1258291,"expiresInSeconds":300,"sourceReviewRequestedAt":null}
                    """))),
            @ApiResponse(responseCode = "400", description = "문서 종류 오류 (`INVALID_INPUT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 또는 현재 제출본의 문서 없음 (`CONTRACT_NOT_FOUND`, `CONTRACT_DOCUMENT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    DownloadResponse download(@Parameter(description = "계약 ID", example = "12") Long id,
            @Parameter(description = "문서 종류", example = "SIGNED_PDF") ContractDocumentType type);

    @Operation(summary = "계약서 PDF 생성본 다운로드", description = "현재 검토 요청 제출본의 서버 생성 PDF 다운로드 URL을 반환합니다. 생성본이 있으면 재사용하고, 없으면 `REVIEW_PENDING`에서 생성합니다. 검토 요청을 취소한 뒤 새로 제출하면 새 제출본으로 생성합니다. 렌더링 중 계약 상태나 제출본이 변경되면 409를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성본 다운로드 정보", content = @Content(schema = @Schema(implementation = DownloadResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "계약 없음 (`CONTRACT_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "생성 불가 상태 또는 생성 중 제출본 변경 (`CONTRACT_STATUS_CONFLICT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "PDF 생성 실패 (`CONTRACT_PDF_GENERATION_FAILED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    DownloadResponse draft(@Parameter(description = "계약 ID", example = "12") Long id, @Parameter(hidden = true) UserPrincipal principal);
}
