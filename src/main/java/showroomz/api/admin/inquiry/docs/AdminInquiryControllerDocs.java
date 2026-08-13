package showroomz.api.admin.inquiry.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.inquiry.dto.AdminInquiryDto;
import showroomz.api.admin.inquiry.type.AdminInquiryStatusFilter;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.cs.type.CsCategory;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - Inquiry (1:1 문의)", description = "어드민 1:1 문의(CS) API — 1:1 문의는 마켓이 아닌 어드민으로만 접수된다")
public interface AdminInquiryControllerDocs {

    @Operation(
            summary = "1:1 문의 목록 조회",
            description = "상태 탭 · 유형 필터 · 검색어로 1:1 문의를 조회합니다.\n\n" +
                    "**상태 탭(`status`)** — 기본 진입 탭은 `ALL`입니다.\n" +
                    "- `ALL`: 전체 / `WAITING`: 접수(미답변) / `ANSWERED`: 답변완료\n\n" +
                    "**유형 필터(`type`)** — 미지정 시 전체 유형. `DELIVERY`, `CANCEL_EXCHANGE_RETURN`, `ORDER_PAYMENT`, `SERVICE`, `ACCOUNT`\n\n" +
                    "**검색(`keyword`)** — 작성자 · 문의 내용 통합 검색(단일 입력)\n\n" +
                    "**경과·SLA** — `elapsedText`는 모든 행에 값이 있습니다(미답변이면 현재까지, 답변 건이면 접수→답변 소요). " +
                    "`slaExceeded=true`면 상태 배지를 `SLA 초과`로 교체합니다(접수 배지와 병기하지 않음). 기준은 경과 3일 초과이며 서버가 계산합니다.\n\n" +
                    "**툴바** — `총 N건`은 `pageInfo.totalResults`, `미답변 N건`은 `statusCounts.waiting`입니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminInquiryDto.ListResponse.class),
                            examples = @ExampleObject(name = "성공 예시", value = "{\n" +
                                    "  \"content\": [\n" +
                                    "    {\n" +
                                    "      \"inquiryId\": 21,\n" +
                                    "      \"type\": \"DELIVERY\",\n" +
                                    "      \"typeName\": \"배송\",\n" +
                                    "      \"content\": \"주문한 지 일주일이 됐는데 배송 조회가 계속 준비중으로만 떠요.\",\n" +
                                    "      \"writerName\": \"오세아\",\n" +
                                    "      \"createdAt\": \"2026-07-15T11:20:00\",\n" +
                                    "      \"answeredAt\": null,\n" +
                                    "      \"elapsedText\": \"3일 2h\",\n" +
                                    "      \"slaExceeded\": true,\n" +
                                    "      \"status\": \"WAITING\"\n" +
                                    "    }\n" +
                                    "  ],\n" +
                                    "  \"pageInfo\": {\n" +
                                    "    \"currentPage\": 1,\n" +
                                    "    \"totalPages\": 1,\n" +
                                    "    \"totalResults\": 6,\n" +
                                    "    \"limit\": 20,\n" +
                                    "    \"hasNext\": false\n" +
                                    "  },\n" +
                                    "  \"statusCounts\": {\n" +
                                    "    \"waiting\": 2,\n" +
                                    "    \"answered\": 4,\n" +
                                    "    \"all\": 6\n" +
                                    "  }\n" +
                                    "}"))),
            @ApiResponse(responseCode = "401", description = "인증 정보가 유효하지 않음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminInquiryDto.ListResponse> getList(
            @Parameter(description = "상태 탭", example = "ALL") AdminInquiryStatusFilter status,
            @Parameter(description = "유형 필터 (미지정 시 전체 유형)", example = "DELIVERY") CsCategory type,
            @Parameter(description = "작성자 · 문의 내용 검색어") String keyword,
            @Parameter(description = "페이징 (page: 1부터, size: 20/50/100)") PagingRequest pagingRequest
    );

    @Operation(
            summary = "미답변 건수 조회 (GNB 배지)",
            description = "`CS·콘텐츠 관리` 및 `1:1 문의` GNB 배지에 표시할 미답변(접수) 건수입니다(§17-7).\n\n" +
                    "상품 문의 답변대기는 브랜드 조치이므로 이 값에 포함하지 않습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<AdminInquiryDto.SummaryResponse> getSummary();

    @Operation(
            summary = "문의 유형 목록 조회",
            description = "유형 필터 셀렉트에 쓰는 5종 목록입니다(FAQ 카테고리와 동일한 분류 체계).\n\n**권한:** ADMIN"
    )
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<List<AdminInquiryDto.TypeOption>> getTypes();

    @Operation(
            summary = "1:1 문의 상세 조회",
            description = "문의 정보 · 스레드 · 처리 패널 · 처리 이력을 조회합니다.\n\n" +
                    "- `orderId`(참조 주문)는 **선택값**입니다. 주문 없이도 문의할 수 있으므로 `null`인 경우를 화면에서 `—`로 처리해야 합니다.\n" +
                    "- `operatorName`(처리자)은 답변완료 상태에서만 값이 있습니다.\n" +
                    "- `elapsedLabel`은 접수면 `미답변 경과`, 답변완료면 `응답 소요`입니다.\n" +
                    "- `prevInquiryId` · `nextInquiryId`는 목록에서 넘어온 `status` · `type` · `keyword` 기준의 순서를 따릅니다. " +
                    "이전/다음 이동을 쓰려면 목록과 동일한 필터 값을 함께 넘겨주세요.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminInquiryDto.DetailResponse.class),
                            examples = @ExampleObject(name = "접수(미답변)", value = "{\n" +
                                    "  \"inquiryId\": 21,\n" +
                                    "  \"inquiryNumber\": \"INQ-20260716-021\",\n" +
                                    "  \"type\": \"CANCEL_EXCHANGE_RETURN\",\n" +
                                    "  \"typeName\": \"취소/교환/반품\",\n" +
                                    "  \"status\": \"WAITING\",\n" +
                                    "  \"slaExceeded\": false,\n" +
                                    "  \"userId\": 108,\n" +
                                    "  \"userName\": \"김민서\",\n" +
                                    "  \"orderId\": null,\n" +
                                    "  \"createdAt\": \"2026-07-16T10:12:00\",\n" +
                                    "  \"answeredAt\": null,\n" +
                                    "  \"elapsedText\": \"2일 4h\",\n" +
                                    "  \"elapsedLabel\": \"미답변 경과\",\n" +
                                    "  \"operatorName\": null,\n" +
                                    "  \"thread\": [\n" +
                                    "    {\n" +
                                    "      \"role\": \"USER\",\n" +
                                    "      \"authorName\": \"김민서\",\n" +
                                    "      \"sentAt\": \"2026-07-16T10:12:00\",\n" +
                                    "      \"content\": \"반품 신청한 지 일주일이 넘었는데 아직도 환불이 안 됐어요.\",\n" +
                                    "      \"imageUrls\": [\"https://example.com/inquiries/img1.jpg\"]\n" +
                                    "    }\n" +
                                    "  ],\n" +
                                    "  \"history\": [\n" +
                                    "    {\n" +
                                    "      \"event\": \"RECEIVED\",\n" +
                                    "      \"occurredAt\": \"2026-07-16T10:12:00\",\n" +
                                    "      \"actorLabel\": \"소비자(김민서)\"\n" +
                                    "    }\n" +
                                    "  ],\n" +
                                    "  \"prevInquiryId\": 22,\n" +
                                    "  \"nextInquiryId\": 20\n" +
                                    "}"))),
            @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\n  \"code\": \"NOT_FOUND_DATA\",\n  \"message\": \"데이터를 찾을 수 없습니다.\"\n}")))
    })
    ResponseEntity<AdminInquiryDto.DetailResponse> getDetail(
            @Parameter(description = "문의 ID", required = true, example = "21") @PathVariable("inquiryId") Long inquiryId,
            @Parameter(description = "목록에서 사용 중인 상태 탭 (이전/다음 순서 기준)", example = "ALL") AdminInquiryStatusFilter status,
            @Parameter(description = "목록에서 사용 중인 유형 필터") CsCategory type,
            @Parameter(description = "목록에서 사용 중인 검색어") String keyword
    );

    @Operation(
            summary = "답변 등록",
            description = "운영자 답변을 등록합니다(§17-4).\n\n" +
                    "- 답변 본문은 **필수**이며 소비자에게 가공 없이 그대로 노출됩니다.\n" +
                    "- 등록은 **1회뿐**이며 수정·삭제할 수 없습니다. 이미 답변된 문의는 400을 반환합니다.\n" +
                    "- 등록 성공이 상태 전이의 유일한 트리거입니다 — 접수 → 답변완료로 전환되고 GNB 배지에서 제외됩니다.\n" +
                    "- 응답의 `unansweredCount`로 GNB 배지를 갱신할 수 있습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "등록 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminInquiryDto.AnswerResponse.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"inquiryId\": 21,\n" +
                                    "  \"inquiryNumber\": \"INQ-20260716-021\",\n" +
                                    "  \"status\": \"ANSWERED\",\n" +
                                    "  \"answeredAt\": \"2026-07-18T14:00:00\",\n" +
                                    "  \"operatorName\": \"김운영\",\n" +
                                    "  \"unansweredCount\": 1\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "이미 답변된 문의이거나 입력값 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "이미 답변된 문의", value = "{\n" +
                                    "  \"code\": \"INQUIRY_ALREADY_ANSWERED\",\n" +
                                    "  \"message\": \"이미 답변이 등록된 문의입니다. 답변은 1회만 등록할 수 있습니다.\"\n" +
                                    "}"))),
            @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "답변 등록 요청 바디",
            required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminInquiryDto.AnswerRequest.class),
                    examples = @ExampleObject(value = "{\n" +
                            "  \"content\": \"안녕하세요, 고객님. 반품 접수 확인했습니다. 7월 17일자로 환불을 실행했습니다.\"\n" +
                            "}"))
    )
    ResponseEntity<AdminInquiryDto.AnswerResponse> registerAnswer(
            @Parameter(description = "문의 ID", required = true, example = "21") @PathVariable("inquiryId") Long inquiryId,
            @RequestBody AdminInquiryDto.AnswerRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );
}
