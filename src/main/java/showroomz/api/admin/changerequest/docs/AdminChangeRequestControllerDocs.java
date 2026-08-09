package showroomz.api.admin.changerequest.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.changerequest.type.AdminChangeRequestStatusFilter;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - Change Request", description = "어드민 변경 요청 검토·승인·반려 API (§16)")
public interface AdminChangeRequestControllerDocs {

    @Operation(
            summary = "목록 조회",
            description = "검토 대기가 항상 위, 그 안에서 경과 내림차순(SLA 초과 건이 최상단)이다. 정렬 셀렉트는 두지 않는다(§16-1). " +
                    "CANCELED는 status=ALL에서만 노출된다.\n\n**권한:** ADMIN"
    )
    ResponseEntity<AdminChangeRequestDto.ListResponse> getList(
            @Parameter(description = "탭", example = "PENDING") @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status,
            @Parameter(description = "브랜드명 검색어") @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(summary = "GNB 배지용 검토 대기 건수", description = "§16-0 상위 '입점 관리' 배지 합산에 쓰인다.\n\n**권한:** ADMIN")
    ResponseEntity<AdminChangeRequestDto.SummaryResponse> getSummary();

    @Operation(
            summary = "반려 사유 드롭다운",
            description = "유형별 6종/5종 정형 사유 + detailRequired(OTHER만 true)를 내려준다(§16-5).\n\n**권한:** ADMIN"
    )
    ResponseEntity<List<AdminChangeRequestDto.RejectReasonOption>> getRejectReasons(
            @Parameter(description = "요청 유형", required = true) @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "상세 조회",
            description = "대조표는 변경 없는 행까지 전부 내려준다. 이전/다음은 status 파라미터가 가리키는 " +
                    "현재 탭의 목록 순서를 따른다(§16-2).\n\n**권한:** ADMIN"
    )
    @ApiResponse(responseCode = "404", description = "존재하지 않는 요청(CHANGE_REQUEST_NOT_FOUND)")
    ResponseEntity<AdminChangeRequestDto.DetailResponse> getDetail(
            @Parameter(description = "요청 ID", required = true) @PathVariable("requestId") Long requestId,
            @Parameter(description = "이전/다음 계산 기준 탭", example = "PENDING") @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status);

    @Operation(
            summary = "승인",
            description = "전체 승인만 있다(부분 승인 없음). 브랜드명 변경 건은 승인 시점에 중복을 재검사한다. " +
                    "정산 계좌 승인 시 통장 사본을 증빙 파일로 교체한다(§7-2).\n\n**권한:** ADMIN"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 성공"),
            @ApiResponse(responseCode = "400", description = "PENDING 상태가 아님(CHANGE_REQUEST_NOT_PENDING) 또는 브랜드명 중복(DUPLICATE_MARKET_NAME)")
    })
    ResponseEntity<AdminChangeRequestDto.ProcessResponse> approve(
            @Parameter(description = "요청 ID", required = true) @PathVariable("requestId") Long requestId,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "반려",
            description = "reasonType이 요청 유형에 맞지 않으면 400이다. OTHER 선택 시에만 reasonDetail이 필수다.\n\n**권한:** ADMIN"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 성공"),
            @ApiResponse(responseCode = "400", description = "PENDING 아님 / 사유 유형 불일치 / 기타 사유 상세 누락")
    })
    ResponseEntity<AdminChangeRequestDto.ProcessResponse> reject(
            @Parameter(description = "요청 ID", required = true) @PathVariable("requestId") Long requestId,
            @Valid @RequestBody AdminChangeRequestDto.RejectRequest request,
            @Parameter(hidden = true) UserPrincipal principal);
}
