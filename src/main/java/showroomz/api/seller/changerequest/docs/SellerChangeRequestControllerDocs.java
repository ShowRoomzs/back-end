package showroomz.api.seller.changerequest.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.seller.changerequest.dto.ChangeRequestBannerResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestCreateResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestFieldOption;
import showroomz.api.seller.changerequest.dto.CreateChangeRequestRequest;
import showroomz.domain.changerequest.type.ChangeRequestType;

import java.util.List;

@Tag(name = "Seller - Change Request", description = "파트너센터 기본정보 변경 요청 API (§15-6·§15-7)")
public interface SellerChangeRequestControllerDocs {

    @Operation(
            summary = "변경 요청 생성 (M1·M2 공통)",
            description = "동일 (브랜드, 유형)에 PENDING 요청이 있으면 409(CHANGE_REQUEST_ALREADY_PENDING)를 반환한다. " +
                    "요청값이 현재값과 같은 항목이 있으면 400(CHANGE_REQUEST_VALUE_UNCHANGED)이다. " +
                    "사업자등록번호처럼 enum에 없는 항목을 보내면 역직렬화 단계에서 400으로 거부된다.\n\n**권한:** SELLER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "접수 성공"),
            @ApiResponse(responseCode = "400", description = "검증 실패(항목 없음/사유 누락/증빙 누락/값 동일 등)"),
            @ApiResponse(responseCode = "409", description = "이미 검토 중인 요청 존재(CHANGE_REQUEST_ALREADY_PENDING)")
    })
    ResponseEntity<ChangeRequestCreateResponse> create(@Valid @RequestBody CreateChangeRequestRequest request);

    @Operation(
            summary = "현재 배너 상태 조회",
            description = "PENDING이거나, 처리완료(APPROVED/REJECTED)인데 아직 [확인]하지 않은 최신 1건을 반환한다. " +
                    "해당 없음이면 200 + 빈 본문(null)이다.\n\n**권한:** SELLER"
    )
    @ApiResponse(responseCode = "200", description = "조회 성공(없으면 null)")
    ResponseEntity<ChangeRequestBannerResponse> getLatest(
            @Parameter(description = "요청 유형", required = true, example = "BUSINESS_INFO")
            @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "모달 진입용 항목 목록",
            description = "M1·M2 모달의 체크박스 항목·라벨·현재값을 내려준다. 사업자등록번호는 이 목록에 없다(§15-1 ③).\n\n**권한:** SELLER"
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<List<ChangeRequestFieldOption>> getFields(
            @Parameter(description = "요청 유형", required = true, example = "BUSINESS_INFO")
            @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "요청 취소",
            description = "PENDING 상태인 본인 요청만 취소할 수 있다. 취소된 행은 CANCELED로 보존된다(감사 기록).\n\n**권한:** SELLER(본인 마켓 요청만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리 성공"),
            @ApiResponse(responseCode = "403", description = "본인 마켓의 요청이 아님"),
            @ApiResponse(responseCode = "400", description = "PENDING 상태가 아님")
    })
    ResponseEntity<Void> cancel(@Parameter(description = "요청 ID", required = true) @PathVariable("requestId") Long requestId);

    @Operation(
            summary = "결과 배너 확인",
            description = "처리완료 배너의 [확인] 버튼 — result_acknowledged_at을 기록해 배너를 닫고 재요청을 연다.\n\n**권한:** SELLER(본인 마켓 요청만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리 성공"),
            @ApiResponse(responseCode = "403", description = "본인 마켓의 요청이 아님")
    })
    ResponseEntity<Void> acknowledge(@Parameter(description = "요청 ID", required = true) @PathVariable("requestId") Long requestId);
}
