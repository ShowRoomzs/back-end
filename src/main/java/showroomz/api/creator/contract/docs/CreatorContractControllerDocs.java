package showroomz.api.creator.contract.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.contract.dto.CreatorContractClausesResponse;
import showroomz.api.creator.contract.dto.CreatorContractDeclineRequest;
import showroomz.api.creator.contract.dto.CreatorContractDetailResponse;
import showroomz.api.creator.contract.dto.CreatorContractDocumentDownloadResponse;
import showroomz.api.creator.contract.dto.CreatorContractListItem;
import showroomz.api.creator.contract.dto.CreatorContractResendRequestResponse;
import showroomz.api.creator.contract.dto.CreatorContractSummaryResponse;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.CreatorContractSortType;
import showroomz.domain.contract.type.CreatorContractTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Contract", description = "쇼룸 스튜디오 계약 관리 API (§25·§27)")
public interface CreatorContractControllerDocs {

    @Operation(
            summary = "계약 목록",
            description = """
                    인플루언서에게 **도착한** 계약을 조회한다(시안 S1).

                    **권한:** CREATOR

                    ### 「도착한 계약」의 정의 — 가시성은 필터가 아니라 권한이다
                    브랜드가 작성중 화면에서 계약 상대를 고르는 순간 `creator_id`가 박힌다.
                    그래서 **내 계약을 전부 내리면 브랜드가 아직 보내지도 않은 계약이 뜬다** —
                    운영자가 문제를 지적해 되돌린 검토 반려 계약까지. 목록·상세·거절·재발송·문서·조항
                    여섯 경로가 전부 같은 판정을 통과한다.

                    - 보이는 상태 6종: `SIGNING` `CONCLUSION_PENDING` `CONCLUDED` `DECLINED` `EXPIRED` `CANCELED`
                    - **작성중 탭은 존재하지 않는다** — 0건으로 표시하는 것이 아니라 탭 값 자체가 없다(§27-1 #1)

                    ### 「내 서명 기한」 열은 서버가 판정한다
                    이 열은 한 종류가 아니라 **상태 × 서명 조합**으로 여섯 갈래다.
                    FE가 이 표를 복제하면 서버 판정과 어긋나므로 `deadline.type`으로 판정 결과를 내린다.

                    | type | 화면 |
                    |---|---|
                    | `DEADLINE` | 기한 날짜(`deadlineAt` 동봉) — `SIGNING` · 내 서명 없음 |
                    | `MY_SIGNED` | 「내 서명 완료」 |
                    | `BOTH_SIGNED` | 「양측 서명 완료」 — `CONCLUSION_PENDING` |
                    | `SIGNED` | 「서명 완료」 — `CONCLUDED` |
                    | `PASSED` | 「기한 경과」 — `EXPIRED` |
                    | `NONE` | 「—」 — `DECLINED` · `CANCELED` |

                    **문구는 서버가 짓지 않는다.** `tone`만 내리고 표시 문자열은 FE가 고른다.
                    `tone`은 `type = DEADLINE`일 때만 `WARNING`이 될 수 있고 **`DANGER`는 이 열에 없다** —
                    기한이 지나면 만료(중립 종결)이지 부정 결과 확정이 아니다.
                    임박 임계값은 D-3으로 집행 중이다(§28-8 D #9 확정 시 알림 시점과 같은 값으로 맞춘다).

                    ### 응답에 없는 것
                    - `createdAt` — 생성일은 브랜드의 사정이다(§27-1 #3). 정렬 기준도 「받은 순」이다
                    - `entryMode` — 스튜디오에 작성 모드가 없다. 진입은 항상 상세다

                    빈 상태(S2)는 서버가 구분하지 않는다 — `totalCount = 0`이면 FE가 그린다.
                    연결코드 안내는 기존 `GET /v1/creator/connections/code`를 쓴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "인플루언서 계정 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<CreatorContractListItem>> getContracts(
            @Parameter(description = "상태 탭 — ALL(기본) / SIGNING / CONCLUSION_PENDING / CONCLUDED / CLOSED. "
                    + "**파트너의 SIGNING 탭과 집합이 다르다** — 파트너는 「서명 진행중 + 체결 처리 대기」 "
                    + "묶음이지만 스튜디오는 둘이 쪼개져 각각 서 있다")
            @RequestParam(required = false) CreatorContractTab tab,
            @Parameter(description = "공구명 · 브랜드명 검색")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 — RECEIVED_DESC(기본 · 받은 순) / DEADLINE_ASC(서명 기한순 · NULL은 뒤) "
                    + "/ START_AT_ASC(공구 시작일순)")
            @RequestParam(required = false) CreatorContractSortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 카운트 · GNB 배지",
            description = """
                    탭별 건수와 **내 서명이 필요한 계약 건수**를 돌려준다(설계서 3).

                    **권한:** CREATOR

                    목록과 분리돼 있다. 파트너와 같은 이유(배지는 계약 화면 밖에서도 폴링된다)에 더해
                    스튜디오 쪽이 더 중요하다 — **배지가 가리키는 것이 「기한이 있는 내 조치」**라서
                    계약 화면에 들어오지 않는 인플루언서가 이 숫자만 보고 움직인다. 놓치면 만료다.

                    ### `actionRequiredCount` — 파트너와 정의가 다르다
                    ```sql
                    status = 'SIGNING' AND creator_signed_at IS NULL
                    ```
                    **`brand_signed_at`을 조건에 넣지 않는다.** S3a는 「내 차례」가 아니라
                    「내 몫이 남은 것」이고, 양측 미서명(S3)에서도 내 몫은 똑같이 남아 있다 —
                    **순서가 아니라 각자 한다**(§27-2). 파트너가 「상대만 서명함」 하나만 배지에 넣은 것과
                    반대인데, 대칭이 깨져서가 아니라 스튜디오 rev.2가 서명 순서 감각을 폐기했기 때문이다.

                    세지 않는 것: 내 서명을 마친 `SIGNING`(되돌릴 수도 없다) · `CONCLUSION_PENDING`
                    (운영자 대기라 내가 할 수 있는 일이 없다) · `CONCLUDED` · 종결 3종.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<CreatorContractSummaryResponse> getSummary();

    @Operation(
            summary = "계약 상세",
            description = """
                    상세 화면 8종(S3 · S3a · S3b · S3c · S6 · S7 · S8 · S9)이 **이 응답 하나**를 쓴다.

                    **권한:** CREATOR

                    서버가 `viewPhase: "S3a"` 같은 값을 만들지 않는다 — 조합을 값으로 만들면
                    조합이 늘 때마다 값이 는다. FE의 분기 근거는 세 필드다.

                    | 화면 | `status` | `signature.brandSignedAt` | `signature.creatorSignedAt` |
                    |---|---|---|---|
                    | S3 | `SIGNING` | NULL | NULL |
                    | S3a | `SIGNING` | 있음 | NULL |
                    | S3b | `SIGNING` | NULL | 있음 |
                    | S3c | `CONCLUSION_PENDING` | 있음 | 있음 |

                    ### 파트너 응답과 **구성이** 다르다 — 라벨이 아니라
                    - `payout`(「내가 받는 금액」) 블록이 **스튜디오에만** 있다. **종결 3종에서는 `null`이다** —
                      값을 내리고 FE가 숨기는 방식은, 숨기는 조건을 FE가 틀리면 성립하지 않은 계약의 금액이
                      「내가 받는 금액」으로 보이는 사고가 된다
                    - `content`(콘텐츠 의무)는 반대로 종결 3종에서도 **그대로 내리고** `obligationAlive: false`를 준다
                    - `items[]`의 필드명이 스튜디오 관점이다 — `myRewardRate` · `brandSupplyQuantity`
                    - `history[]`는 **화이트리스트 7종**만. 브랜드의 작성중 저장 · 검토 요청 · **검토 반려 사유**는
                      화면에 안 그려도 JSON에 실리면 이미 유출된 것이다. 필터는 쿼리 단계에서 걸린다
                    - `permissions`는 **5종**이고 파트너 7종과 교집합이 거의 없다

                    ### 응답에서 덜어낸 것
                    `createdAt` · `review` 블록 전체(반려 사유 포함) · `warningFlags` · `sourceContractId` ·
                    `fixedFeeNoticeAgreedAt` · `version` · 브랜드 쪽 환불·정산 이력.
                    스텝퍼의 「운영자 검토 통과」에 필요한 `reviewApprovedAt` 하나만 `stepper`로 옮겼다.

                    ### `signature.asOf` — 「방금 서명했는데 왜 반영이 안 됐지」의 해명 장치
                    서명 값은 운영자가 모두싸인을 보고 손으로 옮겨 적는다(모두싸인 API가 없다).
                    **서명 페이지에서 돌아와 상세를 다시 조회해도 `creatorSignedAt`은 아직 NULL일 가능성이 높다.**
                    그래서 진행 중 계약에서 `asOf`를 **항상** 내린다 — 아직 한 번도 갱신하지 않은 계약은
                    `signature_requested_at`으로 대체한다.

                    **S11(내 서명 완료 모달)을 위한 API는 없다.** 서버가 띄우는 것이 아니라
                    FE가 서명 링크 복귀 시점에 자체적으로 띄운다. **서명 링크도 응답에 없다** — 우리가 갖고 있지 않다.

                    ### 부수 효과 — 열람 기록
                    최초 진입이면 `creator_viewed_at`을 찍는다. `POST /{id}/view`를 만들지 않은 이유는,
                    FE가 호출을 빠뜨리거나 순서를 바꾸면 **브랜드 화면이 「열람 안 함」으로 거짓말**을 하기 때문이다.
                    최초 1회 CAS라 멱등이고, 실패해도 조회 응답을 깨지 않는다. 이력에는 남기지 않는다 —
                    「상대가 열람함」이 뜨면 브랜드가 인플루언서의 접속을 들여다보는 화면이 된다.

                    ### 404를 주는 경우 — **403이 아니다**
                    남의 계약도, 내 `creator_id`가 박혀 있지만 아직 도착 전인 계약(`DRAFT`·`REVIEW_PENDING`·
                    `REVIEW_REJECTED`)도 똑같이 `404`다. 403은 「있는데 못 본다」는 뜻이고, 그러면 인플루언서가
                    **브랜드가 자기 앞으로 계약을 작성 중이라는 사실**을 알게 된다. 반려된 계약이라면
                    「나한테 보내려다 운영자에게 막혔다」까지 읽힌다.
                    이미 종결된 계약은 **200**이다 — 종결 계약도 읽을 수 있다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 아직 도착하지 않은 계약",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorContractDetailResponse> getContract(
            @Parameter(description = "계약 ID") @PathVariable Long contractId,
            @Parameter(description = "목록 탭 — **이전/다음 이동 계산용 선택 파라미터**. "
                    + "이전/다음 두 건은 현재 목록의 정렬·필터 안에서의 이웃이라 목록 조건을 모르면 계산할 수 없다. "
                    + "셋 다 없으면 navigation의 두 값이 null이고 FE는 버튼을 비활성한다")
            @RequestParam(required = false) CreatorContractTab tab,
            @Parameter(description = "목록 검색어 — 이전/다음 이동 계산용")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "목록 정렬 — 이전/다음 이동 계산용")
            @RequestParam(required = false) CreatorContractSortType sort);

    @Operation(
            summary = "표준 조항",
            description = """
                    **이 계약에 고정된** 조항 버전을 읽는다(설계서 6-3).

                    **권한:** CREATOR

                    파트너는 작성 중(조항 버전 미고정)에도 전문을 봐야 해서 계약과 무관한
                    `/contracts/clauses` 경로가 필요했다. **스튜디오가 보는 계약은 전부
                    `clause_version_id`가 고정된 뒤**이므로 계약에 매달린 경로가 더 정확하다 —
                    문안이 개정된 뒤에 **내가 서명한 계약의 조항과 화면에 뜨는 조항이 달라지면 안 된다.**

                    입력 컨트롤이 하나도 없는 읽기 전용 응답이다(§25-7).
                    문안이 법률 검토 대기인 조항은 `fullTitle`·`fullBody`가 `null`이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "미도착 계약 또는 조항 버전 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorContractClausesResponse> getClauses(@PathVariable Long contractId);

    @Operation(
            summary = "계약 문서 다운로드",
            description = """
                    계약 문서를 내려받는다(S6). 상태에 따라 받을 수 있는 종류가 다르다.

                    **권한:** CREATOR

                    | 상태 | 받을 수 있는 문서 |
                    |---|---|
                    | `SIGNING` · `CONCLUSION_PENDING` | `GENERATED_DRAFT` 계약서 생성본 — 운영자가 내려받는 것과 같은 파일 |
                    | `CONCLUDED` | `SIGNED_PDF` 서명 완료 계약서 · `AUDIT_TRAIL` 감사 추적 인증서 |
                    | 종결 3종 | 없음 |

                    서명 PDF·인증서는 체결 시 발급된다 — 그 전까지는 생성본이 계약서다.
                    생성본이 아직 만들어지지 않았거나 체결 문서가 업로드 전이면 404다.

                    시안 S3의 「받은 계약서 보기」가 무엇을 여는지(모두싸인 링크 / 화면 내 조건 요약)는
                    미확정이라 **그 엔드포인트는 만들지 않았다**(설계서 미결 #5).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "미도착 계약 또는 문서 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorContractDocumentDownloadResponse> getDocument(
            @Parameter(description = "계약 ID") @PathVariable Long contractId,
            @Parameter(description = "문서 종류 — SIGNED_PDF / AUDIT_TRAIL") @PathVariable ContractDocumentType type);

    @Operation(
            summary = "계약 거절",
            description = """
                    받은 계약을 거절한다(S5). `SIGNING` → `DECLINED` · `close_actor_type = CREATOR`.

                    **권한:** CREATOR

                    ### 허용 조건
                    `SIGNING`이고 **내 서명이 아직 없을 때만**이다. 상태 판정을 읽어서 하지 않고
                    조건부 UPDATE의 WHERE에 `creator_signed_at IS NULL`을 함께 건다 —
                    **내가 거절 모달을 열어 둔 사이에 운영자가 내 서명을 체크**할 수 있기 때문이다.
                    서명한 계약이 거절로 종결되면 모두싸인에는 내 서명이 남고 우리 시스템은 거절인 상태가 된다.

                    `CONCLUSION_PENDING`은 **409**다 — 양측 서명이 끝난 계약이다.

                    ### 사유
                    사유 구분은 **필수** 5종(`CONDITION_RENEGOTIATION` `SCHEDULE_MISMATCH`
                    `NOT_FIT_SHOWROOM` `CONTENT_BURDEN` `ETC`)이고 메모는 **선택**이다.
                    브랜드의 취소 사유와 **별도 enum**이다 — 한 enum에 합치면 거절 모달에
                    브랜드용 선택지가 뜰 수 있는 구조가 된다. `close_reason_code` 컬럼은 공유하되
                    해석은 `closeActorType`이 정한다.

                    **사유는 브랜드에게 그대로 전달된다**(시안 S5 고지). 서버가 가공하지 않는다.

                    ### 부수 효과
                    이력 `DECLINED` append · 브랜드·운영자에게 통지.
                    **연결은 건드리지 않는다** — 시안 S7 「거절은 이 계약만 종결시킵니다 —
                    연결이 끊기지는 않습니다」.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 완료 — 갱신된 상세를 돌려준다"),
            @ApiResponse(responseCode = "400", description = "사유 구분 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 아직 도착하지 않은 계약",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "이미 서명함(CONTRACT_ALREADY_SIGNED) 또는 거절 불가 상태(CONTRACT_DECLINE_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorContractDetailResponse> decline(
            @Parameter(description = "계약 ID") @PathVariable Long contractId,
            @Valid @RequestBody CreatorContractDeclineRequest request);

    @Operation(
            summary = "서명 안내 다시 받기",
            description = """
                    전자서명 안내를 다시 보내 달라고 **요청**한다.

                    **권한:** CREATOR

                    **상태는 변하지 않는다.** `contract_resend_request` 한 행이 생길 뿐이다
                    (`requester_type = 'CREATOR'`).

                    허용 조건은 `permissions.canRequestResend`와 같다 — `SIGNING`이고 내 서명이
                    아직 없을 때만이다. 이미 서명한 사람에게 재발송할 이유가 없다. 그 밖은 409.

                    **중복 억제**: 미처리(`handled_at IS NULL`) 요청이 이미 있으면 새 행을 만들지 않고
                    기존 요청을 200으로 돌려준다(`alreadyRequested: true`) — 어드민 큐에 같은 계약이
                    여러 줄 쌓이는 것을 막는다. 횟수 제한 정책은 §28-8 D #7로 미정이다.

                    ### 화면 문구 주의
                    **실제 재발송은 우리가 하지 않는다.** 운영자가 모두싸인에서 한다.
                    응답 문구가 「재발송했습니다」가 되면 안 된다 — **「요청했습니다」**다.
                    이 구분이 흐려지면 인플루언서가 오지 않을 메일을 기다린다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 접수 — 기존 미처리 요청이면 alreadyRequested=true"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 아직 도착하지 않은 계약",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "서명이 필요한 계약이 아님(CONTRACT_RESEND_NOT_ALLOWED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CreatorContractResendRequestResponse> requestResend(
            @Parameter(description = "계약 ID") @PathVariable Long contractId);
}
