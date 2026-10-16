package showroomz.api.admin.brand.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.brand.dto.AdminBrandDto;
import showroomz.api.admin.brand.type.AdminBrandSort;
import showroomz.api.admin.brand.type.AdminBrandTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Brand Member", description = "어드민 회원 관리 · 브랜드 — 상세 · 정지 · 탈퇴 심사 · 메모 · 이력(ui-admin-15 · 52 설계서)")
public interface AdminBrandControllerDocs {

    @Operation(summary = "브랜드 회원 목록",
            description = """
                    입점이 끝난 브랜드(셀러 승인 완료)만 나옵니다. 목록 시안(14)이 아직 없어 상세의 「목록 · 이전 · 다음」을 성립시키는 최소 구성입니다.

                    - `tab`: `ALL`(기본) · `ACTIVE`(휴면 포함) · `SUSPENDED` · `WITHDRAWN`
                    - `keyword`: `BRD-`로 시작하면 회원번호 정확 일치(접두사 뒤가 숫자가 아니면 0건) · 그 외 브랜드명 부분 일치
                    - `sort`: `RECENT_JOINED`(기본) · `BRAND_NO`
                    - 탈퇴 승인 대기는 **회원 상태가 아니라** `withdrawalPending` 플래그입니다(승인 전까지 브랜드는 활성).
                    - `summary.withdrawalPending` 은 검색 조건과 무관한 전체 대기 건수입니다.""")
    AdminBrandDto.ListResponse list(@Parameter(description = "탭") AdminBrandTab tab,
                                    @Parameter(description = "BRD-회원번호 또는 브랜드명") String keyword,
                                    @Parameter(description = "정렬") AdminBrandSort sort,
                                    PagingRequest paging);

    @Operation(summary = "브랜드 회원 상세",
            description = """
                    좌측 사실 조회 7카드(기본 · 사업자 · 정산 · 배송반품 · 운영 현황 · 연결 인플루언서 · 관리자 메모)와 우측 레일(현재 상태 · 진행 중 칩 · 탈퇴 신청 심사 · 처리 이력)을 한 응답으로 내립니다.

                    - **계좌번호는 가운데 마스킹**(`110-****-3841`)이고 원본은 응답에 없습니다 — 전체 값은 정산 관리에서 조회합니다.
                    - 업태 · 종목 · 배송 방법 · 택배사 연동은 내리지 않습니다(§52-1 · 수집하지 않거나 브랜드 단위 값이 없음).
                    - 「최근 문의」 카드는 없습니다 — 파트너센터 1:1 문의가 정의되지 않았습니다(§55 A-5). 대신 `operatorThread`(운영팀 소통 스레드)를 내립니다.
                    - `rail.pendingChips` 는 1건 이상인 칩만 · `rail.histories` 는 최신 3건(탈퇴 심사 중이면 2건) · `memos` 는 최신 1건입니다.
                    - 탈퇴 계정은 `purged=true` 이고 파기된 값은 `null` 입니다(「— 파기됨」). 배송 · 반품 정책 · 브랜드명 · 가입일 · 입점 승인일은 보존됩니다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND — 없거나 입점이 끝나지 않은 브랜드",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Detail detail(Long marketId, UserPrincipal principal);

    @Operation(summary = "브랜드 정지(M1)",
            description = """
                    로그인은 허용하고 **새로 벌이는 일만** 막습니다 — 상품 등록 · 새 계약 작성 · 검토 요청 · 인플루언서 연결 요청 · 오픈 전 공구의 오픈. 진행 중 공구의 판매 · 주문 처리 · 상품 문의 답변 · 기본정보(계좌 포함) 수정은 그대로입니다.

                    - 사유 6종 · `OTHER` 만 `reasonDetail` 필수.
                    - **브랜드 몫 지급 정지는 사유가 정합니다** — `SETTLEMENT_INFO_MISMATCH` · `BUSINESS_INFO_FALSIFIED` 면 지급 예정 브랜드 몫이 보류(`BRAND_SUSPENDED`)되고, 해제 다음 영업일에 지급됩니다. 인플루언서 · 플랫폼 몫은 예정대로입니다.
                    - 운영팀 소통 스레드에 「계정 정지」 시스템 카드가 자동으로 붙습니다(소명 경로).
                    - 탈퇴 심사 중에도 정지할 수 있습니다. 응답은 갱신된 레일입니다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정지 완료"),
            @ApiResponse(responseCode = "400", description = "BRAND_SUSPEND_DETAIL_REQUIRED · INVALID_INPUT_VALUE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BRAND_ALREADY_SUSPENDED · BRAND_WITHDRAWN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Rail suspend(Long marketId, AdminBrandDto.SuspendRequest request, UserPrincipal principal);

    @Operation(summary = "브랜드 정지 해제",
            description = """
                    해제는 이 화면에서만 합니다. `reason` 은 선택(비면 `RESOLVED`)이고 `WITHDRAWN` 은 고를 수 없습니다. 지급 정지 중이던 브랜드 몫은 해제 다음 영업일 지급 예정으로 돌아가고, 운영팀 스레드에 「정지 해제」 카드가 붙습니다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 완료"),
            @ApiResponse(responseCode = "409", description = "BRAND_NOT_SUSPENDED · BRAND_WITHDRAWN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Rail release(Long marketId, AdminBrandDto.ReleaseRequest request, UserPrincipal principal);

    @Operation(summary = "탈퇴 승인(M2)",
            description = """
                    **되돌릴 수 없습니다.** 승인 직전에 조건 4종(진행 · 준비 중 공구 · 체결 전 계약 · 미종결 주문 · 진행 중 정산)을 다시 판정하고, 하나라도 남으면 409 `BRAND_WITHDRAWAL_CONDITION_UNMET` 에 `unmet`(조건 → 남은 건수)을 실어 거절합니다.

                    승인되면 회원 상태가 `WITHDRAWN` 이 되고 로그인이 막히며(이메일 · 비밀번호 폐기 · 토큰 삭제), 사업자 · 정산 · 담당자 정보가 파기되고, 상품은 전건 미진열 · 작성중 계약은 삭제 · 남은 연결은 전건 종료됩니다. 주문 · 정산 · 계약 · 이력 · 배송 정책은 보존됩니다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료"),
            @ApiResponse(responseCode = "404", description = "BRAND_WITHDRAWAL_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BRAND_WITHDRAWAL_NOT_PENDING · BRAND_WITHDRAWAL_CONDITION_UNMET(unmet 포함) · BRAND_WITHDRAWN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Rail approveWithdrawal(Long marketId, Long requestId, UserPrincipal principal);

    @Operation(summary = "탈퇴 반려(M3)",
            description = "반려 사유는 신청 조건과 같은 축입니다. `OTHER` 면 `note`(브랜드에 전달할 안내) 필수. 회원 상태는 바뀌지 않고(내내 활성) 브랜드는 바로 재신청할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료"),
            @ApiResponse(responseCode = "400", description = "BRAND_WITHDRAWAL_REJECT_DETAIL_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "BRAND_WITHDRAWAL_NOT_PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Rail rejectWithdrawal(Long marketId, Long requestId, AdminBrandDto.WithdrawalRejectRequest request,
                                        UserPrincipal principal);

    @Operation(summary = "처리 이력 전체",
            description = "최신순. 이 화면의 조치(정지 · 해제 · 탈퇴 신청 · 승인 · 반려 · 메모)와 가입 신청 · 입점 승인 · 변경 요청(접수 · 승인 · 반려)을 합쳐 내립니다. 파기 대상이 아닙니다.")
    AdminBrandDto.HistoryPage histories(Long marketId, PagingRequest paging);

    @Operation(summary = "관리자 메모 목록", description = "최신순 · `editable` = 작성자 본인 ∧ 24시간 이내 ∧ 탈퇴 계정 아님.")
    AdminBrandDto.MemoPage memos(Long marketId, PagingRequest paging, UserPrincipal principal);

    @Operation(summary = "관리자 메모 기입", description = "누적 기입 · 1 ~ 300자 · 브랜드 비노출. 탈퇴 계정은 409 `BRAND_WITHDRAWN`.")
    AdminBrandDto.Memo createMemo(Long marketId, AdminBrandDto.MemoRequest request, UserPrincipal principal);

    @Operation(summary = "관리자 메모 수정", description = "작성자 본인만(403 `BRAND_MEMO_NOT_AUTHOR`) · 24시간 이내만(403 `BRAND_MEMO_EDIT_WINDOW_CLOSED`).")
    AdminBrandDto.Memo updateMemo(Long marketId, Long memoId, AdminBrandDto.MemoRequest request, UserPrincipal principal);

    @Operation(summary = "관리자 메모 삭제", description = "수정과 같은 조건 · 삭제된 메모는 목록에서 빠집니다.")
    ResponseEntity<Void> deleteMemo(Long marketId, Long memoId, UserPrincipal principal);

    @Operation(summary = "연결 인플루언서 전체", description = "연결중 먼저(최근 연결순) → 연결 종료(최근 종료순). 행 클릭 → 인플루언서 상세.")
    AdminBrandDto.ConnectedCreatorPage connectedCreators(Long marketId, PagingRequest paging);
}
