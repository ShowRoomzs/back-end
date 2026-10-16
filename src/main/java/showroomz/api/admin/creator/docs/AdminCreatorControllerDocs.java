package showroomz.api.admin.creator.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.creator.dto.AdminCreatorDto.*;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PageResponse;

@Tag(name = "Admin - Influencer Member", description = """
        어드민 회원 관리 › 인플루언서 상세(ui-admin-18 rev.1 · 53 설계서). 좌측은 사실 조회, 우측 레일은 판단과 조치다.
        본인확인 정보는 기본 마스킹이고 마스킹 해제(M4)로만 열린다. 계좌번호는 해제 경로가 없다(전체 값은 정산 관리에서만).
        정지 · 탈퇴 규칙은 §51 — 정지는 로그인 허용 · 새로 벌이는 일만 차단, 탈퇴는 신청 → 운영자 승인이다.""")
public interface AdminCreatorControllerDocs {

    @Operation(summary = "인플루언서 상세 — 상단 4카드 + 레일", description = """
            기본 정보 · 본인확인 정보(마스킹) · 사업자 정보 · 정산 정보 + 레일(현재 상태 · 이슈 스레드 · 탈퇴 신청 심사 · 처리 이력 최신 N건).
            요청한 운영자의 유효한 마스킹 해제 세션이 있으면 본인확인 정보가 실값으로 내려간다(10분 고정 만료).
            탈퇴 회원은 레이아웃 그대로 destroyed = true 로 「— 파기됨」을 그린다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상세"),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Detail detail(@Parameter(description = "creatorId(INF- 뒤 숫자)", example = "3018") Long creatorId,
                  @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "운영 현황 KPI 4 + 쇼룸 활동", description = """
            연결 브랜드 · 진행중 공구 · 정산 진행 중 리워드 · 조정 협의(경고색은 조정 협의만).
            쇼룸 활동은 앱 내 기록만 — 공구 게시물 · 검토 반려 · 숨김 횟수(사실 기록) · 일반 게시물 · 쇼룸 팔로워(앱 내) · 소통 스레드.""")
    Overview overview(Long creatorId);

    @Operation(summary = "최근 정산", description = "정산 관리와 같은 단계 배지 · 조정 협의 행에 합의 기한. 기본 5건 · total 은 전체 건수.")
    SettlementList settlements(Long creatorId, @Parameter(description = "건수(1~50)", example = "5") int size);

    @Operation(summary = "계약", description = """
            인플루언서에게 도착한 계약(서명 진행중 이후). 고정 지급비 · 지급 시점 · 지급 시점 도래 시각 · 브랜드 지급 기록(자기 신고).
            「기록 없음 ∧ 시점 경과」의 경고색은 FE 판단 — 플랫폼은 고정 지급비를 보관 · 보증하지 않아 배지 · 조치 버튼이 없다.""")
    PageResponse<ContractRow> contracts(Long creatorId, int page, int size);

    @Operation(summary = "연결 브랜드", description = "연결중 먼저 · 연결 종료 아래. 요청중 · 거절은 내리지 않는다.")
    ConnectionList connections(Long creatorId);

    @Operation(summary = "처리 이력", description = "최신순 · 저장 이력 + 가입 신청 · 입점 승인(입점 신청에서 파생). 파기 대상 아님.")
    HistoryPage histories(Long creatorId, int page, int size);

    @Operation(summary = "관리자 메모 목록", description = "최신순 · 인플루언서 비노출 · editable = 작성자 본인 ∧ 24시간 이내.")
    MemoPage memos(Long creatorId, int page, int size, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 기입", description = "누적 기입 · 300자 이내(서버 검증). 탈퇴 회원은 409 CREATOR_WITHDRAWN.")
    Memo createMemo(Long creatorId, MemoRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 수정", description = "작성자 본인(403 CREATOR_MEMO_NOT_AUTHOR) ∧ 24시간 이내(403 CREATOR_MEMO_EDIT_WINDOW_CLOSED).")
    Memo updateMemo(Long creatorId, Long memoId, MemoRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 삭제", description = "수정과 같은 조건 · soft delete.")
    ResponseEntity<Void> deleteMemo(Long creatorId, Long memoId, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "본인확인 정보 마스킹 해제(M4)", description = """
            정형 사유 4종 필수(CS 응대 · 정산 · 원천징수 확인 · 계약 · 분쟁 대응 · 수사기관 협조) · 상세 사유 · 기타 없음.
            이 인플루언서 1건 · 이 운영자 · 10분 고정 만료. 열람 사실은 처리 이력에 즉시 기록. 응답은 실값이 든 상세.
            최고관리자 한정(403 CREATOR_PRIVACY_UNMASK_FORBIDDEN) · 탈퇴 파기 회원은 409 CREATOR_PRIVACY_UNMASK_NOT_AVAILABLE.""")
    Detail unmask(Long creatorId, UnmaskRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "지금 잠금", description = "이 운영자의 유효 해제 세션을 즉시 닫는다. 화면을 떠날 때도 부른다. 세션이 없어도 204.")
    ResponseEntity<Void> lock(Long creatorId, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "정지(M1)", description = """
            사유 6종 · 기타만 상세 필수. 로그인은 유지되고 새 게시물 게시 · 계약 서명(서명 요청 발송)이 막힌다.
            정산 정보 불일치만 리워드 지급을 멈춘다(지급 예정 행 → 보류). 운영팀 채널에 「계정 정지」 카드가 자동 등록된다.
            409 CREATOR_ALREADY_SUSPENDED · CREATOR_WITHDRAWN · 400 CREATOR_SUSPEND_DETAIL_REQUIRED.""")
    Detail suspend(Long creatorId, SuspendRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "정지 해제", description = """
            해제 사유는 선택(기본 RESOLVED). 지급 정지였으면 보류 행을 다시 판정해 다음 영업일 지급 예정으로 올린다(다른 보류 사유가
            남으면 보류 유지). 「정지 해제」 카드 자동 등록. 409 CREATOR_NOT_SUSPENDED.""")
    Detail release(Long creatorId, ReleaseRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "탈퇴 승인(M2)", description = """
            조건(끝나지 않은 공구 · 계약 · 미정산)을 다시 판정해 하나라도 남으면 409 CREATOR_WITHDRAWAL_CONDITION_UNMET(건수는 메시지에)
            — 반려로 처리한다. 승인하면 쇼룸 비공개 · 브랜드 연결 일괄 종료 · 본인확인 · 정산 정보 즉시 파기 · 스튜디오 자격 해지
            (소비자 계정 유지). 주민등록번호 · 사업자등록번호는 세무 보존(파기 시점은 법률 자문 대기). 되돌릴 수 없다.""")
    Detail approveWithdrawal(Long creatorId, Long requestId, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "탈퇴 반려(M3)", description = "사유는 조건과 같은 축 · 기타만 안내 필수. 회원 상태는 바뀌지 않는다(신청만 종결).")
    Detail rejectWithdrawal(Long creatorId, Long requestId, RejectWithdrawalRequest request,
                            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "모달 셀렉트 사유 목록", description = "정지 · 해제 · 탈퇴 반려 · 마스킹 해제 사유의 코드와 라벨 — 단일 소스.")
    ReasonOptions reasonOptions();
}
