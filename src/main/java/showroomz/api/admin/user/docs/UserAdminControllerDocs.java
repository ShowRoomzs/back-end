package showroomz.api.admin.user.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - User", description = "관리자 일반 유저 관리 API")
public interface UserAdminControllerDocs {

    @Operation(
            summary = "소비자 목록 조회",
            description = "어드민 소비자 목록입니다. 컬럼 8종 — 회원번호 · 닉네임 · 이름 · 휴대폰 · 가입 수단 · 가입일 · 누적 주문 · 상태\n\n" +
                    "**마스킹은 서버가 끝냅니다.** 이름은 가운데 1자, 휴대폰은 가운데 4자리를 가린 값만 내려갑니다. " +
                    "목록에는 해제 경로가 없으므로 원본은 응답에 포함되지 않습니다.\n\n" +
                    "**검색 3축** — 입력 형태로 축을 하나 고릅니다(OR로 묶지 않습니다).\n" +
                    "- `CST-`로 시작 → 회원번호 정확히 일치 (접두사 뒤가 숫자가 아니면 0건)\n" +
                    "- 숫자 4자리 → 휴대폰 **뒤 4자리**\n" +
                    "- 그 외 → 닉네임 부분 일치\n\n" +
                    "이메일 검색 축은 없습니다 — 소셜 이메일(특히 Apple 릴레이)은 연락 가능한 주소가 아니라 화면에서 뺐습니다.\n\n" +
                    "**요약(`summary`)** — 상태 조건만 제외하고 검색어·가입 수단 필터는 그대로 반영한 건수입니다. " +
                    "`newSuspendedIn30Days`는 **정지 탭에서만** 값이 있고 다른 탭에서는 null입니다.\n\n" +
                    "**누적 주문** — 취소되지 않은 주문 상품이 하나라도 있는 주문의 수입니다(취소 포함 여부는 기획 확인 대기).\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}",
            parameters = {
                    @Parameter(name = "page", description = "페이지 번호 (1부터 시작)", example = "1", in = ParameterIn.QUERY),
                    @Parameter(name = "size", description = "페이지당 항목 수", example = "20", in = ParameterIn.QUERY),
                    @Parameter(
                            name = "tab",
                            description = "상태 탭 — 기본 진입은 전체",
                            example = "ALL",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"ALL", "ACTIVE", "SUSPENDED", "WITHDRAWN"})
                    ),
                    @Parameter(
                            name = "keyword",
                            description = "회원번호(CST-) · 닉네임 · 휴대폰 뒤 4자리",
                            example = "CST-88231",
                            in = ParameterIn.QUERY
                    ),
                    @Parameter(
                            name = "providerType",
                            description = "가입 수단",
                            example = "KAKAO",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"KAKAO", "NAVER", "APPLE", "GOOGLE"})
                    ),
                    @Parameter(
                            name = "sort",
                            description = "정렬 — 기본은 최근 가입순",
                            example = "RECENT_JOINED",
                            in = ParameterIn.QUERY,
                            schema = @Schema(allowableValues = {"RECENT_JOINED", "ORDER_COUNT_DESC", "MEMBER_NO"})
                    )
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserDto.ListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "전체 탭 조회 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"userId\": 88231,\n" +
                                                    "      \"memberNo\": \"CST-88231\",\n" +
                                                    "      \"nickname\": \"홍길동\",\n" +
                                                    "      \"maskedName\": \"홍*동\",\n" +
                                                    "      \"maskedPhone\": \"010-****-1234\",\n" +
                                                    "      \"providerType\": \"KAKAO\",\n" +
                                                    "      \"joinedAt\": \"2026-02-01T10:12:00\",\n" +
                                                    "      \"orderCount\": 14,\n" +
                                                    "      \"status\": \"NORMAL\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"userId\": 88190,\n" +
                                                    "      \"memberNo\": \"CST-88190\",\n" +
                                                    "      \"nickname\": \"유리\",\n" +
                                                    "      \"maskedName\": \"박*은\",\n" +
                                                    "      \"maskedPhone\": \"010-****-2031\",\n" +
                                                    "      \"providerType\": \"APPLE\",\n" +
                                                    "      \"joinedAt\": \"2026-01-22T09:03:00\",\n" +
                                                    "      \"orderCount\": 0,\n" +
                                                    "      \"status\": \"SUSPENDED\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 117,\n" +
                                                    "    \"totalResults\": 2340,\n" +
                                                    "    \"size\": 20,\n" +
                                                    "    \"hasNext\": true\n" +
                                                    "  },\n" +
                                                    "  \"summary\": {\n" +
                                                    "    \"total\": 2340,\n" +
                                                    "    \"active\": 2268,\n" +
                                                    "    \"suspended\": 12,\n" +
                                                    "    \"withdrawn\": 60,\n" +
                                                    "    \"newSuspendedIn30Days\": null\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\"code\": \"FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminUserDto.ListResponse> getUsers(
            @RequestParam(value = "tab", defaultValue = "ALL") AdminUserTab tab,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "providerType", required = false) ProviderType providerType,
            @RequestParam(value = "sort", defaultValue = "RECENT_JOINED") AdminUserSort sort,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "소비자 모달 사유 목록",
            description = "정지 모달(M1) · 해제 모달(M4) · 마스킹 해제 모달(M2) 셀렉트의 코드와 라벨입니다 — enum 단일 소스라 FE 가 목록을 하드코딩하지 않습니다(55 계획서 M8 · M3-3). " +
                    "행 모양은 브랜드 · 인플루언서 상세의 같은 API 와 같습니다(`code` · `label` · `detailRequired` · `holdsPayout`).\n\n" +
                    "- **정지 사유 6종** — `detailRequired=true`(기타)를 고르면 `reasonDetail`이 필수입니다. 소비자 정지는 지급과 무관해 `holdsPayout`은 null 입니다.\n" +
                    "- **해제 사유 3종** — 상세 입력란이 없어 `detailRequired`는 모두 false 입니다. `TERM_ENDED`(제재 기간 종료)를 운영자도 고를 수 있습니다 — " +
                    "브랜드 · 인플루언서 해제 사유에는 없는 값입니다(D18). 정지 기간이 끝난 정지는 시스템이 같은 사유로 자동 해제합니다.\n" +
                    "- **마스킹 해제 사유 4종** — 정형 사유만입니다. 「기타」 · 상세 사유가 없습니다(이 4종을 벗어나는 열람은 하지 않는다).\n" +
                    "- **`suspensionTerm`** — 정지 모달 미리보기(1021 7-2): 「이의 신청 기한 · 통지 후 `appealBusinessDays`영업일 이상 · " +
                    "`appealDeadlineAt`까지」(응답 시각 기준 · 공휴일 반영) · 「소명 검토 · 접수 후 `appealReviewBusinessDays`영업일」" +
                    "(미정이면 null → 「[정책값 미정]」). 저장 값은 정지 응답의 `term.appealDeadlineAt` 입니다. 브랜드 · 인플루언서와 같은 모양입니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.ReasonOptions.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"suspensionReasons\": [\n" +
                                    "    { \"code\": \"ABUSIVE_LANGUAGE\", \"label\": \"욕설·비방\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"RETURN_ABUSE\", \"label\": \"반품 남용\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"FRAUDULENT_ORDER_SUSPECTED\", \"label\": \"부정 주문 의심\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"PAYMENT_FRAUD_SUSPECTED\", \"label\": \"결제 도용 의심\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"MULTIPLE_REPORTS\", \"label\": \"신고 다수 접수\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"OTHER\", \"label\": \"기타(직접 입력)\", \"detailRequired\": true, \"holdsPayout\": null }\n" +
                                    "  ],\n" +
                                    "  \"releaseReasons\": [\n" +
                                    "    { \"code\": \"RESOLVED\", \"label\": \"소명 확인 완료\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"MISTAKE\", \"label\": \"오처리 정정\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"TERM_ENDED\", \"label\": \"제재 기간 종료\", \"detailRequired\": false, \"holdsPayout\": null }\n" +
                                    "  ],\n" +
                                    "  \"privacyUnmaskReasons\": [\n" +
                                    "    { \"code\": \"CS_RESPONSE\", \"label\": \"CS 응대\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"DELIVERY_ACCIDENT\", \"label\": \"배송 사고\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"PAYMENT_RETURN_DISPUTE\", \"label\": \"결제·반품 분쟁\", \"detailRequired\": false, \"holdsPayout\": null },\n" +
                                    "    { \"code\": \"INVESTIGATION_COOPERATION\", \"label\": \"수사기관 협조\", \"detailRequired\": false, \"holdsPayout\": null }\n" +
                                    "  ],\n" +
                                    "  \"suspensionTerm\": { \"appealBusinessDays\": 3, \"appealDeadlineAt\": \"2026-10-22T23:59:59\", \"appealReviewBusinessDays\": null }\n" +
                                    "}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.ReasonOptions> reasonOptions();

    @Operation(
            summary = "소비자 상세 조회",
            description = "소비자 상세입니다(ui-admin-13 rev.7). 좌측은 사실 조회(계정 · 본인확인 · 최근 주문 · 최근 문의 · 활동 · 배송지 · 메모), 우측 레일은 판단과 조치(상태 · 진행 중 · 판단 메타 · 이력)입니다.\n\n" +
                    "**계정 정보** — `memberNo`(CST-) · `navigation`(회원번호 순 이전 · 다음 소비자 — 목록 필터와 무관) · `lastLoginAt` · `lastLoginDevice`(최근 로그인 이력의 User-Agent) · " +
                    "`notification`(앱 푸시 = 기기 토큰 있음 ∧ 토글 하나라도 켬 · 토글 4개 · 광고성 동의와 변경 시각).\n\n" +
                    "**본인확인(`identity`)** — **마스킹은 서버가 끝냅니다.** 이름 가운데 · 생년월일 연도만 · 휴대폰 가운데 4자리. 성별은 해제 대상이 아닙니다. " +
                    "내외국인 · 통신사는 수집하지 않아 null 입니다. 재인증하면 인증 시각을 덮어써 `firstVerifiedAt` 대신 `renewedAt`이 채워집니다. " +
                    "최상위 `birthday`는 `identity.birthday`와 같은 값(마스킹)입니다 — 원본은 내리지 않습니다. " +
                    "**이 운영자의 유효한 열람 세션**(`POST /{userId}/privacy-unmask`)이 있으면 이름 · 생년월일(+만 나이) · 휴대폰과 배송지 상세가 실값이고, " +
                    "`unmask`에 해제됨 · 처리자 · 시각 · 남은 초 · 직전 사유가 실립니다. 다른 운영자의 세션은 적용되지 않습니다.\n\n" +
                    "**최근 주문(`recentOrders`)** — 최근 5건. 행은 어드민 주문 목록(`GET /v1/admin/orders?userId=`)과 같고, 「전체 주문 내역 보기 ›」도 그 목록입니다. " +
                    "상태 칸의 「반품완료」 · 「교환완료」는 `groups[].claim.label`(가장 최근 반품 · 교환 · 없으면 null)이고, 배송 이행 상태는 `groups[].statusLabel` 그대로입니다. " +
                    "**최근 문의(`recentInquiries`)** — 1:1 · 상품 문의를 최신순으로 합친 3건(번호는 각 관리 화면의 INQ- · QNA- · 제목은 본문 앞 40자).\n\n" +
                    "**배송지(`addresses`)** — 기본 배송지 먼저 · 최근 수정순. 마스킹 중에는 시 · 군 · 구까지만 · 상세주소 · 우편번호 null · 수령인 · 연락처 마스킹. " +
                    "「최근 사용 주문일」은 주문에 배송지 id 가 없어 항상 null 입니다(보류).\n\n" +
                    "**진행 중(`pendingChips`)** — 열린 문의(WARNING) · 배송중 · 취소 요청 · 반품·교환 · 환불 실패 중 1건 이상만. " +
                    "`links[]`가 칩이 가리키는 목록(`target`) · 그 목록 API 에 그대로 붙일 쿼리(`query` — 탭 · 상태 · `userId`) · 그 목록 건수를 싣습니다. " +
                    "**칩 `count` = `links[].count` 합 = 그 목록들의 `pageInfo.totalResults` 합**입니다. 열린 문의는 1:1 문의 · 상품 문의 두 목록으로 갈릴 수 있습니다(0건인 쪽은 빠짐). " +
                    "**판단 메타(`judgement`)** — 누적 주문 · 누적 구매액 · 반품·교환 주문 수와 비율(÷ 누적 주문) · 정지 이력(user_sanction 행 수 · 직전 해제된 정지) · 가입 후 경과.\n\n" +
                    "**탈퇴 회원** — 본인확인 · 배송지 · 알림 수신은 `destroyed = true`, 최근 로그인 null(「— 파기됨」). 주문 · 문의 기록은 보존이라 그대로이고, 판단 메타는 거래 값이 null 이며 가입 후 경과 · 정지 이력만 남습니다.\n\n" +
                    "**현재 상태 카드(`statusCard`)** — 정지 상태면 사유 · 처리자 · 일시 · 상세 사유 · **소명 문의**(`appealInquiries` — 해제 모달의 「소명 문의 번호」 · " +
                    "`inquiryNumber`는 1:1 문의 관리와 같은 INQ-YYYYMMDD-NNN)가 함께 내려갑니다. " +
                    "`actions.canSuspend` = 정상일 때만 · `actions.canRelease` = 정지일 때만 · 탈퇴 회원은 둘 다 false(운영자 액션 없음). " +
                    "탈퇴 회원은 `withdrawal.withdrawnAt`(탈퇴 일시 — 처리 이력 「회원 탈퇴」 행과 같은 시각)이 함께 오고, 그 밖에는 `withdrawal = null` 입니다. " +
                    "탈퇴 회원도 열린 정지가 있으면 그대로 보입니다 — 같은 계정으로 재가입하면 그 정지로 돌아옵니다.\n\n" +
                    "**메모(`memos`)** — 최신 3건 + 전체 건수. `editable`은 요청 운영자 기준(작성자 본인 ∧ 24시간 이내 ∧ 탈퇴 아님)입니다.\n\n" +
                    "**처리 이력(`histories`)** — 최신 5건 + 전체 건수. 저장 사건(정지 · 해제 · 메모 · 개인정보 열람)과 파생 사건(가입 · 탈퇴 · 재가입 · 정지 소명 문의 접수)을 합친 최신순입니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserDto.UserDetailResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "유저를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유저 없음",
                                            value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AdminUserDto.UserDetailResponse> getUserDetail(
            @Parameter(description = "조회할 유저 ID", required = true, example = "1", in = ParameterIn.PATH)
            @PathVariable("userId") Long userId,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 본인확인 정보 마스킹 해제",
            description = "본인확인 카드 헤더 [마스킹 해제] → 사유 모달(M2)의 실행입니다(55 계획서 M3-3). 흐름 · 규칙은 인플루언서 상세의 같은 API 와 같습니다.\n\n" +
                    "- **대상** — 이름 · 생년월일(+만 나이) · 휴대폰과 배송지 상세(상세주소 · 우편번호 · 수령인 · 연락처 — 시안 A4 「주소는 본인확인 정보와 같은 열람 통제」). CI/DI 는 화면에 없고 해제 대상이 아닙니다.\n" +
                    "- **범위** — 이 소비자 1건 · 이 운영자 · **10분 고정 만료**(조작으로 연장되지 않음) · [지금 잠금]. 다른 운영자의 상세에는 적용되지 않습니다. " +
                    "다른 소비자로 이동 · 새로고침 때는 FE 가 `privacy-lock`을 부릅니다.\n" +
                    "- **사유** — 정형 4종만(`reason-options`의 `privacyUnmaskReasons`) · 기타 · 상세 사유 없음.\n" +
                    "- **기록** — 열람 로그 1행 + 처리 이력 「개인정보 열람」(경고색 · detail = 사유 라벨)이 즉시 남습니다. " +
                    "**유효한 세션이 있으면 새 행을 만들지 않습니다**(두 번 눌러도 로그 1건 · 사유도 바뀌지 않음).\n" +
                    "- **권한** — 최고관리자 한정이 기획이지만 등급 모델이 없어 지금은 ADMIN 이면 통과합니다(`unmask.allowed`).\n\n" +
                    "응답은 **해제된 상세 전체**입니다(`identity.unmasked = true` · `unmask`).\n\n" +
                    "**권한:** ADMIN"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminUserDto.UnmaskRequest.class),
                    examples = @ExampleObject(value = "{\n  \"reason\": \"CS_RESPONSE\"\n}")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "해제 — 해제된 상세 전체",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.UserDetailResponse.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"userId\": 88231,\n" +
                                    "  \"memberNo\": \"CST-88231\",\n" +
                                    "  \"identity\": { \"destroyed\": false, \"unmasked\": true, \"name\": \"홍길동\", \"birthday\": \"1990.03.17\", \"age\": 36,\n" +
                                    "    \"gender\": \"FEMALE\", \"genderLabel\": \"여성\", \"nationality\": null, \"phone\": \"010-2841-1234\", \"carrier\": null,\n" +
                                    "    \"firstVerifiedAt\": \"2026-02-01T10:22:00\", \"renewedAt\": null },\n" +
                                    "  \"unmask\": { \"allowed\": true, \"unmasked\": true, \"operatorName\": \"김운영\", \"unmaskedAt\": \"2026-08-14T14:22:00\",\n" +
                                    "    \"expiresAt\": \"2026-08-14T14:32:00\", \"remainingSeconds\": 600, \"lastReason\": \"CS_RESPONSE\" },\n" +
                                    "  \"addresses\": { \"destroyed\": false, \"total\": 1, \"content\": [ { \"addressId\": 5, \"defaultAddress\": true,\n" +
                                    "    \"address\": \"서울특별시 마포구 월드컵로 12\", \"detailAddress\": \"3층\", \"zipCode\": \"03925\", \"recipientName\": \"홍길동\",\n" +
                                    "    \"phoneNumber\": \"010-2841-1234\", \"lastUsedOrderAt\": null } ] }\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "사유 누락 · 없는 사유",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "해제 권한 없음(`USER_PRIVACY_UNMASK_FORBIDDEN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_PRIVACY_UNMASK_FORBIDDEN\", \"message\": \"마스킹 해제 권한이 없습니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "회원 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴(파기) 회원(`USER_PRIVACY_UNMASK_NOT_AVAILABLE`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_PRIVACY_UNMASK_NOT_AVAILABLE\", \"message\": \"파기된 정보는 해제할 수 없습니다.\"}")))
    })
    ResponseEntity<AdminUserDto.UserDetailResponse> unmask(
            @Parameter(description = "유저 ID", required = true, example = "88231", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.UnmaskRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 본인확인 정보 지금 잠금",
            description = "[지금 잠금] — 이 운영자의 유효한 열람 세션을 닫습니다(즉시 재마스킹). 세션이 없어도 204 입니다(멱등). " +
                    "다른 소비자 상세로 이동 · 새로고침 · 화면 이탈 때도 FE 가 부릅니다. 다른 운영자의 세션은 닫지 않습니다. " +
                    "잠근 뒤 다시 열려면 사유를 다시 고릅니다(프리필은 10분 자동 만료 뒤에만 — `unmask.lastReason`).\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "잠금 완료(세션이 없어도 성공)"),
            @ApiResponse(responseCode = "404", description = "회원 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> lock(
            @Parameter(description = "유저 ID", required = true, example = "88231", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 계정 이용 정지",
            description = "정지 모달(M1)의 실행입니다. 「변경할 상태」를 받지 않습니다 — 버튼이 이미 목적지를 말합니다(§25-4).\n\n" +
                    "**사유 6종** — `ABUSIVE_LANGUAGE`(욕설·비방) · `RETURN_ABUSE`(반품 남용) · `FRAUDULENT_ORDER_SUSPECTED`(부정 주문 의심) · " +
                    "`PAYMENT_FRAUD_SUSPECTED`(결제 도용 의심) · `MULTIPLE_REPORTS`(신고 다수 접수) · `OTHER`(기타 — `reasonDetail` 필수)\n\n" +
                    "**효과(§51-1)** — 로그인은 그대로 · 새로 벌이는 일만 막힙니다: 구매(장바구니 담기 · 주문서 · 주문 생성 · 결제 재시도) · 상품 문의 · 팔로우 · 좋아요 · 게시물 신고 · 닉네임/프로필 사진 변경. " +
                    "진행 중 주문 · 클레임 · 배송지 · 1:1 문의는 그대로입니다. 앱에는 정형 사유만 보이고 상세 사유는 보이지 않습니다. 정지 푸시가 커밋 뒤 나갑니다.\n\n" +
                    "**정지 기간(`endsOn`)은 선택 입력입니다**(1021 기-02). 넣으면 종료일(포함) 다음 날 00:00 이후 첫 자동 해제 배치(매일 00:05 KST · 기동 시 1회)가 " +
                    "사유 `TERM_ENDED`(제재 기간 종료)로 해제합니다 — 운영자 해제와 같은 푸시 · 토스트가 나갑니다. 비우면 기간 없는 정지이고 운영자가 해제할 때까지 이어집니다. " +
                    "대상은 소비자(USER) 계정뿐입니다 — 인플루언서는 인플루언서 상세에서 정지합니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminUserDto.SuspendRequest.class),
                    examples = {
                            @ExampleObject(name = "정형 사유", value = "{\n  \"reason\": \"ABUSIVE_LANGUAGE\",\n  \"reasonDetail\": \"상품 문의 3건에 욕설 · 비방 반복\"\n}"),
                            @ExampleObject(name = "기타(상세 필수)", value = "{\n  \"reason\": \"OTHER\",\n  \"reasonDetail\": \"타인 명의 계정 공유 정황 — 본인 확인 전까지 정지\"\n}")
                    }))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "정지 완료 — 갱신된 상태 카드 + 처리 이력 최신 5건",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.StatusResult.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"statusCard\": {\n" +
                                    "    \"status\": \"SUSPENDED\",\n" +
                                    "    \"sanction\": {\n" +
                                    "      \"sanctionId\": 17,\n" +
                                    "      \"reason\": \"ABUSIVE_LANGUAGE\",\n" +
                                    "      \"reasonLabel\": \"욕설·비방\",\n" +
                                    "      \"reasonDetail\": \"상품 문의 3건에 욕설 · 비방 반복\",\n" +
                                    "      \"suspendedAt\": \"2026-10-10T14:20:00\",\n" +
                                    "      \"suspendedByName\": \"김운영\",\n" +
                                    "      \"appealInquiries\": []\n" +
                                    "    },\n" +
                                    "    \"withdrawal\": null,\n" +
                                    "    \"actions\": { \"canSuspend\": false, \"canRelease\": true }\n" +
                                    "  },\n" +
                                    "  \"histories\": {\n" +
                                    "    \"total\": 4,\n" +
                                    "    \"content\": [\n" +
                                    "      { \"historyId\": 52, \"eventType\": \"SUSPENDED\", \"label\": \"계정 이용 정지\", \"tone\": \"DANGER\",\n" +
                                    "        \"detail\": \"욕설·비방 — 상품 문의 3건에 욕설 · 비방 반복\", \"actorType\": \"ADMIN\", \"actorName\": \"김운영\",\n" +
                                    "        \"refType\": \"SANCTION\", \"refId\": 17, \"occurredAt\": \"2026-10-10T14:20:00\" },\n" +
                                    "      { \"historyId\": null, \"eventType\": \"SIGNED_UP\", \"label\": \"가입\", \"tone\": \"NEUTRAL\",\n" +
                                    "        \"detail\": null, \"actorType\": \"CONSUMER\", \"actorName\": \"소비자 본인\",\n" +
                                    "        \"refType\": null, \"refId\": null, \"occurredAt\": \"2026-02-01T10:00:00\" }\n" +
                                    "    ]\n" +
                                    "  }\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "사유 누락 · 기타 사유 상세 누락 · 상세 500자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "기타 상세 누락", value = "{\"code\": \"USER_SUSPEND_DETAIL_REQUIRED\", \"message\": \"기타 사유를 선택하면 상세 사유를 입력해야 합니다.\"}"),
                                    @ExampleObject(name = "사유 누락", value = "{\"code\": \"INVALID_INPUT\", \"message\": \"정지 사유를 선택해 주세요.\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "소비자(USER) 아님 · 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "이미 정지(`USER_ALREADY_SUSPENDED`) · 탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_ALREADY_SUSPENDED\", \"message\": \"이미 정지된 회원입니다.\"}")))
    })
    ResponseEntity<AdminUserDto.StatusResult> suspend(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.SuspendRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 정지 해제",
            description = "해제 모달의 실행입니다. **사유 필수 · 상세 사유란 없음**(§25-4) — `RESOLVED`(소명 확인 완료) · `MISTAKE`(오처리 정정) · `TERM_ENDED`(제재 기간 종료).\n\n" +
                    "정지 기간(`endsOn`)이 있는 정지는 종료일 다음 날 시스템이 `TERM_ENDED`로 **자동 해제**합니다(1021 기-02 · 운영자 해제와 같은 푸시 · 토스트). " +
                    "`TERM_ENDED`는 운영자도 고를 수 있습니다 — 기간 없이 정지했다가 합의한 기간이 지나 수동으로 푸는 경우입니다(55 계획서 D18 · 브랜드 · 인플루언서 해제 사유에는 없습니다).\n\n" +
                    "해제는 회원 상세에서만 합니다(1:1 문의 답변 화면에 해제 버튼을 두지 않습니다). 즉시 모든 기능이 다시 열리고, 해제 푸시가 나가며, 앱은 다음 진입 때 해제 토스트를 1회 띄웁니다. " +
                    "정지 이력과 사유는 그대로 보존됩니다. 사유 기록 없이 정지된 옛 회원도 해제할 수 있습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminUserDto.ReleaseRequest.class),
                    examples = @ExampleObject(value = "{\n  \"reason\": \"RESOLVED\"\n}")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "해제 완료 — 갱신된 상태 카드 + 처리 이력 최신 5건",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.StatusResult.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"statusCard\": {\n" +
                                    "    \"status\": \"NORMAL\",\n" +
                                    "    \"sanction\": null,\n" +
                                    "    \"withdrawal\": null,\n" +
                                    "    \"actions\": { \"canSuspend\": true, \"canRelease\": false }\n" +
                                    "  },\n" +
                                    "  \"histories\": {\n" +
                                    "    \"total\": 6,\n" +
                                    "    \"content\": [\n" +
                                    "      { \"historyId\": 58, \"eventType\": \"SUSPENSION_RELEASED\", \"label\": \"정지 해제\", \"tone\": \"INFO\",\n" +
                                    "        \"detail\": \"소명 확인 완료\", \"actorType\": \"ADMIN\", \"actorName\": \"김운영\",\n" +
                                    "        \"refType\": \"SANCTION\", \"refId\": 17, \"occurredAt\": \"2026-10-14T09:00:00\" },\n" +
                                    "      { \"historyId\": null, \"eventType\": \"APPEAL_RECEIVED\", \"label\": \"정지 소명 문의 접수\", \"tone\": \"NEUTRAL\",\n" +
                                    "        \"detail\": null, \"actorType\": \"CONSUMER\", \"actorName\": \"소비자 본인\",\n" +
                                    "        \"refType\": \"INQUIRY\", \"refId\": 903, \"occurredAt\": \"2026-10-11T08:30:00\" }\n" +
                                    "    ]\n" +
                                    "  }\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "해제 사유 누락(`USER_RELEASE_REASON_REQUIRED`) · 소비자 해제 사유 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_RELEASE_REASON_REQUIRED\", \"message\": \"해제 사유를 선택해 주세요.\"}"))),
            @ApiResponse(responseCode = "404", description = "소비자(USER) 아님 · 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "정지 아님(`USER_NOT_SUSPENDED`) · 탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "정지 아님", value = "{\"code\": \"USER_NOT_SUSPENDED\", \"message\": \"정지 상태가 아닌 회원입니다.\"}"),
                                    @ExampleObject(name = "탈퇴 회원", value = "{\"code\": \"USER_SANCTION_WITHDRAWN\", \"message\": \"탈퇴한 회원입니다. 조치할 수 없습니다.\"}")
                            }))
    })
    ResponseEntity<AdminUserDto.StatusResult> release(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.ReleaseRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "소비자 처리 이력",
            description = "처리 이력 전체(최신순)입니다. 저장 사건 — 정지 · 해제 · 메모 기입/수정/삭제. 파생 사건(`historyId = null`) — 가입 · 탈퇴 · 재가입 · 정지 소명 문의 접수(`refType = INQUIRY`).\n\n" +
                    "옛 상태 변경 API로 처리된 정지 · 활성 전이도 이관돼 있습니다(처리자 · 사유 미기록).\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.HistoryPage.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"total\": 3,\n" +
                                    "  \"page\": 1,\n" +
                                    "  \"size\": 20,\n" +
                                    "  \"content\": [\n" +
                                    "    { \"historyId\": 53, \"eventType\": \"MEMO_CREATED\", \"label\": \"관리자 메모 기입\", \"tone\": \"NEUTRAL\",\n" +
                                    "      \"detail\": null, \"actorType\": \"ADMIN\", \"actorName\": \"김운영\", \"refType\": \"MEMO\", \"refId\": 3,\n" +
                                    "      \"occurredAt\": \"2026-10-10T14:25:00\" },\n" +
                                    "    { \"historyId\": 52, \"eventType\": \"SUSPENDED\", \"label\": \"계정 이용 정지\", \"tone\": \"DANGER\",\n" +
                                    "      \"detail\": \"욕설·비방 — 상품 문의 3건에 욕설 · 비방 반복\", \"actorType\": \"ADMIN\", \"actorName\": \"김운영\",\n" +
                                    "      \"refType\": \"SANCTION\", \"refId\": 17, \"occurredAt\": \"2026-10-10T14:20:00\" },\n" +
                                    "    { \"historyId\": null, \"eventType\": \"SIGNED_UP\", \"label\": \"가입\", \"tone\": \"NEUTRAL\",\n" +
                                    "      \"detail\": null, \"actorType\": \"CONSUMER\", \"actorName\": \"소비자 본인\", \"refType\": null, \"refId\": null,\n" +
                                    "      \"occurredAt\": \"2026-02-01T10:00:00\" }\n" +
                                    "  ]\n" +
                                    "}"))),
            @ApiResponse(responseCode = "404", description = "회원 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.HistoryPage> getHistories(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "페이지(1부터)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "소비자 약관 동의 내역",
            description = "동의 대상 약관 3종(이용 약관 · 개인정보처리방침 · 마케팅 동의)의 현재 동의 상태 · 시각 · 버전입니다(58 계획 5-29).\n\n" +
                    "- 이용 약관 · 개인정보처리방침의 시각은 가입 시각, 마케팅 동의는 마지막 동의 · 철회 시각입니다\n" +
                    "- 동의 기록에는 버전이 없어 **동의 시각에 시행 중이던 버전**으로 파생합니다(소비자 문서 우선 · 없으면 전체 문서). 찾을 수 없으면 `version = null`\n" +
                    "- 「운영정책」은 열람 전용이라 포함하지 않습니다\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.ConsentList.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"content\": [\n" +
                                    "    { \"termsType\": \"TERMS_OF_SERVICE\", \"termsName\": \"쇼룸즈 이용약관\", \"version\": \"v1.0\", \"agreed\": true, \"agreedAt\": \"2026-02-01T10:00:00Z\" },\n" +
                                    "    { \"termsType\": \"PRIVACY_POLICY\", \"termsName\": \"개인정보처리방침\", \"version\": \"v1.1\", \"agreed\": true, \"agreedAt\": \"2026-02-01T10:00:00Z\" },\n" +
                                    "    { \"termsType\": \"MARKETING_CONSENT\", \"termsName\": \"마케팅 동의\", \"version\": null, \"agreed\": false, \"agreedAt\": \"2026-05-03T09:12:00Z\" }\n" +
                                    "  ]\n" +
                                    "}"))),
            @ApiResponse(responseCode = "404", description = "회원 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.ConsentList> getConsents(@Parameter(description = "소비자 id", example = "1") Long userId);

    @Operation(
            summary = "관리자 메모 목록",
            description = "누적 메모(최신순)입니다(§25-6). `editable` = 작성자 본인 ∧ 24시간 이내 ∧ 탈퇴 아님. " +
                    "옛 단일 메모는 「이전 단일 메모(작성자 미기록)」 한 건으로 이관돼 있고 수정 · 삭제할 수 없습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.MemoPage.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"total\": 2,\n" +
                                    "  \"content\": [\n" +
                                    "    { \"memoId\": 3, \"authorName\": \"김운영\", \"content\": \"반품 사유 반복 — 기준 미달이라 정지하지 않음, 다음 반품 시 재검토\",\n" +
                                    "      \"createdAt\": \"2026-10-10T14:25:00\", \"modifiedAt\": null, \"editable\": true },\n" +
                                    "    { \"memoId\": 1, \"authorName\": \"이전 단일 메모(작성자 미기록)\", \"content\": \"VIP 고객 — 응대 시 유의\",\n" +
                                    "      \"createdAt\": \"2026-03-02T09:00:00\", \"modifiedAt\": null, \"editable\": false }\n" +
                                    "  ]\n" +
                                    "}"))),
            @ApiResponse(responseCode = "404", description = "회원 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.MemoPage> getMemos(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "페이지(1부터)", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "관리자 메모 기입",
            description = "덮어쓰기가 아니라 누적입니다. 공백 제거 후 1 ~ 300자(서버 검증). 소비자에게 노출되지 않지만 개인정보 열람청구 시 제공 대상이 될 수 있습니다. 탈퇴 회원은 조회 전용(409).\n\n" +
                    "**권한:** ADMIN"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminUserDto.MemoRequest.class),
                    examples = @ExampleObject(value = "{\n  \"content\": \"반품 사유 반복 — 기준 미달이라 정지하지 않음, 다음 반품 시 재검토\"\n}")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "기입 성공 — 처리 이력에 「관리자 메모 기입」이 남는다",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.Memo.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"memoId\": 3,\n" +
                                    "  \"authorName\": \"김운영\",\n" +
                                    "  \"content\": \"반품 사유 반복 — 기준 미달이라 정지하지 않음, 다음 반품 시 재검토\",\n" +
                                    "  \"createdAt\": \"2026-10-10T14:25:00\",\n" +
                                    "  \"modifiedAt\": null,\n" +
                                    "  \"editable\": true\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"INVALID_INPUT\", \"message\": \"메모는 1자 이상 300자 이내로 입력해 주세요.\"}"))),
            @ApiResponse(responseCode = "404", description = "회원 없음 — USER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_SANCTION_WITHDRAWN\", \"message\": \"탈퇴한 회원입니다. 조치할 수 없습니다.\"}")))
    })
    ResponseEntity<AdminUserDto.Memo> createMemo(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @RequestBody AdminUserDto.MemoRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "관리자 메모 수정",
            description = "작성자 본인 · 작성 후 24시간 내에만 고칠 수 있습니다. 저장하면 `modifiedAt`이 채워집니다(「수정됨」).\n\n" +
                    "**권한:** ADMIN"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminUserDto.MemoRequest.class),
                    examples = @ExampleObject(value = "{\n  \"content\": \"반품 사유 반복 — 3회째부터 정지 검토\"\n}")))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공 — `modifiedAt` 이 채워진다",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDto.Memo.class),
                            examples = @ExampleObject(value = "{\n" +
                                    "  \"memoId\": 3,\n" +
                                    "  \"authorName\": \"김운영\",\n" +
                                    "  \"content\": \"반품 사유 반복 — 3회째부터 정지 검토\",\n" +
                                    "  \"createdAt\": \"2026-10-10T14:25:00\",\n" +
                                    "  \"modifiedAt\": \"2026-10-10T16:02:00\",\n" +
                                    "  \"editable\": true\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성자 아님(`USER_MEMO_NOT_AUTHOR`) · 24시간 경과(`USER_MEMO_EDIT_WINDOW_CLOSED`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "작성자 아님", value = "{\"code\": \"USER_MEMO_NOT_AUTHOR\", \"message\": \"작성자만 수정·삭제할 수 있습니다.\"}"),
                                    @ExampleObject(name = "24시간 경과", value = "{\"code\": \"USER_MEMO_EDIT_WINDOW_CLOSED\", \"message\": \"작성 후 24시간이 지난 메모는 수정·삭제할 수 없습니다.\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "메모 없음(`USER_MEMO_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\": \"USER_MEMO_NOT_FOUND\", \"message\": \"존재하지 않는 메모입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminUserDto.Memo> updateMemo(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "메모 ID", required = true, example = "3", in = ParameterIn.PATH) @PathVariable("memoId") Long memoId,
            @RequestBody AdminUserDto.MemoRequest request,
            @Parameter(hidden = true) UserPrincipal principal
    );

    @Operation(
            summary = "관리자 메모 삭제",
            description = "작성자 본인 · 작성 후 24시간 내에만 지울 수 있습니다. 삭제해도 처리 이력의 기입 기록은 남습니다.\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 성공 — 목록에서 빠지고 처리 이력에 「관리자 메모 삭제」가 남는다"),
            @ApiResponse(responseCode = "403", description = "작성자 아님(`USER_MEMO_NOT_AUTHOR`) · 24시간 경과(`USER_MEMO_EDIT_WINDOW_CLOSED`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "메모 없음(`USER_MEMO_NOT_FOUND`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴 회원(`USER_SANCTION_WITHDRAWN`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteMemo(
            @Parameter(description = "유저 ID", required = true, example = "1", in = ParameterIn.PATH) @PathVariable("userId") Long userId,
            @Parameter(description = "메모 ID", required = true, example = "3", in = ParameterIn.PATH) @PathVariable("memoId") Long memoId,
            @Parameter(hidden = true) UserPrincipal principal
    );
}
