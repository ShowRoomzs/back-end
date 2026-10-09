package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공구 상세 — B1~B7a 22종이 <b>이 응답 하나</b>를 쓴다(설계서 4-4). 화면 분기는 FE가 값으로 고른다.
 *
 * <p>값이 없는 블록은 null이다. 특히 {@code sales}는 상태에 따라 <b>서버가 내려주지 않는다</b> — 종료 화면에
 * KPI를 두지 않는 것은 의미 규칙이고(§30-4), 응답에 값이 있으면 3서피스 중 어느 한 곳은 결국 그린다.
 */
@Schema(description = "공구 상세 — 상세 화면 22종과 실행 API 응답이 함께 쓴다. 조건 밖의 블록은 null이다")
public record GroupBuyDetailResponse(
        @Schema(description = "공구 요약 — 항상") Summary groupBuy,
        @Schema(description = "기간 — 항상") Timeline timeline,
        @Schema(description = "상대 인플루언서 — 항상") Counterparty counterparty,
        @Schema(description = "원본 계약 — 항상") ContractRef contract,
        @Schema(description = "공구 상품 = 계약 상품 전부 — 항상") List<Item> items,
        @Schema(description = "고정 지급비 — 항상(값이 없으면 내부 필드가 null)") FixedFee fixedFee,
        @Schema(description = "인플루언서 콘텐츠 의무 — 항상") ContentDuty contentDuty,
        @Schema(description = "준비 게이트 3개 — PREPARING·READY에서만", nullable = true) Readiness readiness,
        @Schema(description = "공구 게시물 — 항상(미작성이면 status만 NOT_WRITTEN이고 나머지 null)") Post post,
        @Schema(description = "판매 실적 — 진행중·중단 예정(LIVE) · 정산완료(SETTLED) · 중단(AT_SUSPENSION)에서만. "
                + "준비중·준비완료·종료는 null이다. 판매 모듈이 없으면 항상 null — 0이 아니다", nullable = true)
        Sales sales,
        @Schema(description = "주문 종결 건수 — 진행중·중단 예정·종료·정산완료·중단에서만. 판매 모듈이 없으면 null(0이 아니다). "
                + "0은 「정산해도 된다」는 뜻이라 모르는 값을 0으로 내리지 않는다", nullable = true)
        OrderClosure orderClosure,
        @Schema(description = "기간 연장 — 항상(요청이 없으면 요청 필드만 null, maxDays·requestCutoffAt은 항상 채워진다)")
        Extension extension,
        @Schema(description = "검토 중(PENDING) 중단·조기 마감 요청 — 요청자 무관. 있으면 연장·조기 마감·중단 요청이 모두 막힌다",
                nullable = true)
        ActiveRequest activeRequest,
        @Schema(description = "가장 최근에 판정(승인·반려)된 요청 — B4b·B4h 반려 결과. 기간 만료로 소멸한 요청은 제외", nullable = true)
        LastDecision lastDecision,
        @Schema(description = "직권 중단 — 진행 중 통지, 없으면 종결을 만든 통지, 없으면 가장 최근 통지. 통지 이력이 없으면 null",
                nullable = true)
        AdminSuspension adminSuspension,
        @Schema(description = "종결 정보 — 종결 3종(ENDED·SETTLED·SUSPENDED)에서만", nullable = true) Closure closure,
        @Schema(description = "종료 후 — 종결 3종(ENDED·SETTLED·SUSPENDED)에서만", nullable = true) AfterEnd afterEnd,
        @Schema(description = "버튼 노출 판정 — 항상") Permissions permissions,
        @Schema(description = "이력 — 최신순(발생 시각 내림차순)") List<HistoryEntry> history,
        @Schema(description = "목록 이웃 — 목록 조건(tab·keyword·sort)이 쿼리로 오지 않으면 둘 다 null. 실행 API 응답에서는 항상 둘 다 null")
        Navigation navigation
) {

    public record Summary(
            @Schema(example = "18") Long groupBuyId,
            @Schema(description = "공구번호 — GB-생성일(YYYYMMDD)-순번", example = "GB-20260806-018") String groupBuyNumber,
            @Schema(description = "공구명 — 계약에서 읽는다", example = "글로우 크림 앵콜 공구") String title,
            @Schema(description = "공구 상태 7종 — PREPARING · READY · IN_PROGRESS · SUSPENSION_SCHEDULED · ENDED · SETTLED · SUSPENDED",
                    example = "IN_PROGRESS")
            GroupBuyStatus status,
            @Schema(description = "상태 배지 문구", example = "진행중") String statusLabel,
            @Schema(description = "상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "SUCCESS")
            GroupBuyTone statusTone,
            @Schema(description = "공구 생성 시각 = 계약 체결 시각") LocalDateTime createdAt,
            @Schema(description = "준비 게이트 3개가 모두 채워진 시각", nullable = true) LocalDateTime readyAt,
            @Schema(description = "스케줄러가 실제로 공구를 연 시각", nullable = true) LocalDateTime openedAt,
            @Schema(description = "실제 종결 시각 — 기간 종료면 종료 예정 시각, 조기 마감·중단이면 판정 시각", nullable = true)
            LocalDateTime endedAt,
            @Schema(description = "종결 유형 — COMPLETED(기간 종료) · EARLY_CLOSED(조기 마감) · SUSPENDED(중단)", nullable = true)
            GroupBuyCloseType closeType,
            @Schema(description = "종결 유형 문구", example = "조기 마감", nullable = true) String closeTypeLabel
    ) {
    }

    @Schema(description = "기간 — 일수는 서버 now(Asia/Seoul) 기준 · 양끝 포함 일자 계산. FE 시계를 믿지 않는다")
    public record Timeline(
            @Schema(description = "시작 일시 — 불변") LocalDateTime startAt,
            @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값") LocalDateTime endAt,
            @Schema(description = "원래 종료 예정(계약) — B4d 「8일 → 15일」 병기의 원본") LocalDateTime originalEndAt,
            @Schema(description = "총 기간 일수(시작일~현재 종료일, 양끝 포함)", example = "8") int totalDays,
            @Schema(description = "진행 N일차 — 시작 전이면 0, 종결 후에는 종결 시점까지", example = "3") int elapsedDays,
            @Schema(description = "시작까지 N일 — 시작 후면 null", nullable = true) Integer daysUntilStart,
            @Schema(description = "종료까지 N일 — 판매 중(진행중·중단 예정)이고 종료 전일 때만", nullable = true) Integer daysUntilEnd,
            @Schema(description = "시작 시각이 지났는데 아직 준비중 — 게이트가 채워지면 다음 tick에 바로 열린다(설계서 7-2 #2)")
            boolean startOverdue
    ) {
    }

    public record Counterparty(
            @Schema(description = "인플루언서 id") Long creatorId,
            @Schema(description = "인플루언서 표시명(쇼룸명)", example = "글로우_지민") String name,
            @Schema(description = "「스레드 열기」 대상 PAIR 스레드 — 연결이 끊겼으면 null", nullable = true) Long pairThreadId
    ) {
    }

    @Schema(description = "「계약서 보기」의 목적지")
    public record ContractRef(
            @Schema(description = "계약 id") Long contractId,
            @Schema(description = "계약번호") String contractNumber,
            @Schema(description = "체결 시각") LocalDateTime concludedAt,
            @Schema(description = "체결 서명자 — 양측 중 나중에 서명한 쪽의 표시명(인플루언서는 쇼룸명, 브랜드는 브랜드명). "
                    + "이력 맨 아래 「계약 체결완료 · concludedAt · 서명자」 줄은 FE 가 그린다. 서명 시각이 없으면 null",
                    example = "글로우_지민", nullable = true) String concludedSignerName
    ) {
    }

    @Schema(description = "공구 상품 = 계약 상품 전부(계약 확정값 · 수정 불가)")
    public record Item(
            @Schema(description = "상품 id — 상품 마스터와 연결되지 않은 항목이면 null", nullable = true) Long productId,
            @Schema(description = "상품명(계약 스냅샷)") String productName,
            @Schema(description = "정가(원)", example = "34000") Integer regularPrice,
            @Schema(description = "공구가(원)", example = "27200") Integer groupBuyPrice,
            @Schema(description = "리워드율(%)", example = "12") BigDecimal rewardRate,
            @Schema(description = "개당 예상 리워드(원) — 공구가 × 리워드율 절사. 계약·정산·계약서 PDF와 같은 계산", example = "3264")
            Long expectedUnitReward,
            @Schema(description = "최소 준비 물량(개) — 옵션별 최소 물량의 합계. 물량 확보 확인의 대상", example = "300") Integer minQuantity,
            @Schema(description = "옵션별 판매가·최소 준비 물량 — 공구가·리워드율은 상품 단위라 옵션마다 같다")
            List<ItemOption> options
    ) {
    }

    @Schema(description = "공구 상품 옵션 — 판매가 = 공구가 + 옵션가")
    public record ItemOption(
            @Schema(description = "옵션(variant) id — 상품 관리에서 지워졌으면 null", example = "301", nullable = true) Long variantId,
            @Schema(description = "옵션명(계약 스냅샷)", example = "2개 세트", nullable = true) String variantName,
            @Schema(description = "옵션 판매가(원) = 공구가 + 옵션가", example = "50000", nullable = true) Integer salePrice,
            @Schema(description = "옵션별 최소 준비 물량(개)", example = "100") Integer minQuantity
    ) {
    }

    public record FixedFee(
            @Schema(description = "고정 지급비(원) — 없으면 null", example = "300000", nullable = true) Integer amount,
            @Schema(description = "지급 시점 — POST_REGISTERED(공구 게시물 등록 후) · GROUP_BUY_ENDED(공구 종료 후) · "
                    + "SETTLEMENT_COMPLETED(정산 완료 후)", nullable = true)
            FixedFeeTrigger trigger,
            @Schema(description = "지급 시점 문구", example = "공구 게시물 등록 후", nullable = true) String triggerLabel,
            @Schema(description = "3서피스 문자 단위 동일 표준 표기 — 서버가 짓는다(§29-9). 고정 지급비가 없으면 null. "
                    + "지급 여부(paidAt)는 싣지 않는다 — 공구 화면은 지급 배지를 두지 않는다",
                    example = "고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급", nullable = true)
            String displayText
    ) {
    }

    @Schema(description = "인플루언서 콘텐츠 의무 — 브랜드 이행 확인의 대상")
    public record ContentDuty(
            @Schema(description = "피드 게시 건수", example = "1") Integer feed,
            @Schema(description = "릴스 게시 건수", example = "1") Integer reels,
            @Schema(description = "스토리 게시 건수", example = "3") Integer story,
            @Schema(description = "콘텐츠 게시 기한") LocalDate dueDate
    ) {
    }

    @Schema(description = "준비 게이트 — 3개가 모두 DONE이면 준비완료(READY)가 된다")
    public record Readiness(@Schema(description = "① 물량 확인 · ② 게시물 제출 · ③ 오픈 승인 순서로 항상 3개") List<Gate> gates) {
    }

    public record Gate(
            @Schema(description = "STOCK_CONFIRMED(① 최소 물량 확보 확인) · POST_SUBMITTED(② 게시물 제출) · OPEN_APPROVED(③ 운영자 오픈 승인)")
            GateKey key,
            @Schema(description = "게이트를 채우는 주체 — ① SELLER · ② CREATOR · ③ ADMIN") GroupBuyActorType actorType,
            @Schema(description = "충족 여부") boolean done,
            @Schema(description = "충족 시각", nullable = true) LocalDateTime doneAt,
            @Schema(description = "DONE(완료) · ACTION_REQUIRED(브랜드 차례 — 경고 톤, ①에만) · WAITING(상대 대기) · "
                    + "REJECTED(오픈 승인 반려 — ③에만)")
            GateState state
    ) {
    }

    public enum GateKey { STOCK_CONFIRMED, POST_SUBMITTED, OPEN_APPROVED }

    public enum GateState { DONE, ACTION_REQUIRED, WAITING, REJECTED }

    @Schema(description = "공구 게시물 — 작성·수정은 인플루언서, 심사·숨김은 운영자 몫이다. 브랜드는 읽기만 한다")
    public record Post(
            @Schema(description = "게시물 상태 8종 — NOT_WRITTEN · WRITING · PENDING_APPROVAL · REJECTED · SCHEDULED · EXPOSED · HIDDEN · CLOSED",
                    example = "EXPOSED")
            GroupBuyPostStatus status,
            @Schema(description = "게시물 상태 배지 문구", example = "노출중") String statusLabel,
            @Schema(description = "게시물 상태 배지 색") GroupBuyTone statusTone,
            @Schema(description = "게시물 제목", nullable = true) String title,
            @Schema(description = "게시물 본문", nullable = true) String content,
            @Schema(description = "심사 제출 시각", nullable = true) LocalDateTime submittedAt,
            @Schema(description = "노출 시작 — 공구 오픈 시각", nullable = true) LocalDateTime openedAt,
            @Schema(description = "노출 종료 — 공구 종결 시각(종결 3종에서만)", nullable = true) LocalDateTime closedAt,
            @Schema(description = "노출 종료 사유 — 공구 종결 유형(종결 3종에서만)", nullable = true) GroupBuyCloseType closeReason,
            @Schema(description = "운영자 오픈 반려 사유 — 반려 기록이 있을 때", nullable = true) Reason rejectReason,
            @Schema(description = "운영자 오픈 반려 시각 — rejectReason이 있을 때만(재제출해도 마지막 반려 시각이 남는다)", nullable = true)
            LocalDateTime rejectedAt,
            @Schema(description = "운영자 숨김 사유 — 숨김 중일 때만", nullable = true) Reason hiddenReason,
            @Schema(description = "숨김 시각 — 숨김 중일 때만", nullable = true) LocalDateTime hiddenAt,
            @Schema(description = "숨김 경과 일수 — 숨김 중일 때만", nullable = true) Integer hiddenDays
    ) {
    }

    public record Reason(
            @Schema(description = "사유 코드") String code,
            @Schema(description = "운영자가 쓴 설명", nullable = true) String detail
    ) {
    }

    public record Sales(
            @Schema(description = "LIVE(실시간 · 진행중/중단 예정) · SETTLED(정산 확정 실적) · AT_SUSPENSION(중단 시점 실적)") SalesBasis basis,
            @Schema(description = "주문 건수(취소·반품 반영)") int orderCount,
            @Schema(description = "판매 금액(원)") long amount,
            @Schema(description = "예상 리워드 합계(원) — 항목별 수량 × 개당 예상 리워드의 합") long rewardAmount,
            @Schema(description = "상품별 판매 수량") List<ItemQuantity> itemQuantities
    ) {
    }

    public enum SalesBasis { LIVE, SETTLED, AT_SUSPENSION }

    public record ItemQuantity(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "판매 수량") int quantity
    ) {
    }

    @Schema(description = "주문 종결 건수(하위주문 단위) — 종결 = 구매확정 + 환불. 경로별 내역은 판매 모듈(주문 관리)이 판정한 값을 그대로 싣는다")
    public record OrderClosure(
            @Schema(description = "전체 주문 건수", example = "312") int totalCount,
            @Schema(description = "종결된 주문 건수", example = "312") int closedCount,
            @Schema(description = "미종결 주문 건수 — 0이어야 정산할 수 있다", example = "0") int unclosedCount,
            @Schema(description = "종결 중 구매확정 — B6 「확정 310」 · 「구매확정 310/312」. 판매 모듈이 모르면 null",
                    example = "310", nullable = true)
            Integer purchaseConfirmedCount,
            @Schema(description = "종결 중 환불(결제 후 취소) — B6 「환불 2」. 판매 모듈이 모르면 null", example = "2", nullable = true)
            Integer refundedCount
    ) {
    }

    @Schema(description = "기간 연장 — 요청이 없으면 status 이하 요청 필드가 null이다. maxDays·requestCutoffAt은 C1 즉시 계산용으로 항상 채워진다")
    public record Extension(
            @Schema(description = "PENDING(응답 대기) · ACCEPTED(수락 — 종료일 변경됨) · REJECTED(인플루언서 거절) · "
                    + "EXPIRED(종료 시각까지 무응답 — 변경 없이 종결)", nullable = true)
            ExtensionRequestStatus status,
            @Schema(description = "요청한 연장 일수", example = "7", nullable = true) Integer days,
            @Schema(description = "요청 사유", nullable = true) String reason,
            @Schema(description = "요청 당시 종료 예정", nullable = true) LocalDateTime beforeEndAt,
            @Schema(description = "수락 시 새 종료 예정 = beforeEndAt + days일", nullable = true) LocalDateTime afterEndAt,
            @Schema(description = "요청 시각", nullable = true) LocalDateTime requestedAt,
            @Schema(description = "응답(수락·거절·만료) 시각", nullable = true) LocalDateTime respondedAt,
            @Schema(description = "CREATOR(수락·명시 거절) / SYSTEM(기간 만료)", nullable = true)
            GroupBuyActorType responseActorType,
            @Schema(description = "인플루언서 거절 사유 코드", nullable = true) String rejectReasonCode,
            @Schema(description = "인플루언서 거절 메모", nullable = true) String rejectMemo,
            @Schema(description = "연장 가능 최대 일수 = 총 기간 상한(30) − 현재 총 일수. 0이면 연장 불가", example = "22") int maxDays,
            @Schema(description = "이 시각 이후엔 연장을 요청할 수 없다 — 현재 종료 12시간 전") LocalDateTime requestCutoffAt
    ) {
    }

    public record ActiveRequest(
            @Schema(description = "요청 id") Long changeRequestId,
            @Schema(description = "SUSPEND(공구 중단) · EARLY_CLOSE(조기 마감)") ChangeRequestType type,
            @Schema(description = "SELLER(브랜드) · CREATOR(인플루언서) — 조기 마감은 브랜드만 요청한다") GroupBuyActorType requesterType,
            @Schema(description = "브랜드명 또는 쇼룸명") String requesterName,
            @Schema(description = "사유 코드", example = "QUALITY_ISSUE") String reasonCode,
            @Schema(description = "사유 문구 — 알 수 없는 코드는 null", example = "상품 품질 이슈", nullable = true) String reasonLabel,
            @Schema(description = "브랜드가 낸 요청일 때만 — 인플루언서 메모는 운영자에게 쓴 글이라 내리지 않는다", nullable = true)
            String memo,
            @Schema(description = "요청 당시 상태 — READY(C4 · 시작 전) / IN_PROGRESS(C2) 문구 분기") GroupBuyStatus statusAtRequest,
            @Schema(description = "요청 시각") LocalDateTime requestedAt
    ) {
    }

    public record LastDecision(
            @Schema(description = "요청 id") Long changeRequestId,
            @Schema(description = "SUSPEND(공구 중단) · EARLY_CLOSE(조기 마감)") ChangeRequestType type,
            @Schema(description = "요청자 — SELLER · CREATOR") GroupBuyActorType requesterType,
            @Schema(description = "APPROVED(승인) · REJECTED(반려)") ChangeRequestStatus result,
            @Schema(description = "운영자 판정 사유 — 양측에 전달되는 글", nullable = true) String decisionReason,
            @Schema(description = "판정 시각", nullable = true) LocalDateTime decidedAt
    ) {
    }

    @Schema(description = "운영자 직권 중단 — NOTICE(사전 통지 → 소명 → 집행/철회) 또는 EMERGENCY(긴급 즉시 집행)")
    public record AdminSuspension(
            @Schema(description = "직권 중단 id") Long adminSuspensionId,
            @Schema(description = "NOTICE(사전 통지 · 제17조①②) · EMERGENCY(긴급 즉시 중단 · 제17조③)") AdminSuspensionKind kind,
            @Schema(description = "NOTICED(통지 중 — 중단 예정) · WITHDRAWN(철회 — 진행중 복귀) · EXECUTED(집행 — 중단) · "
                    + "LAPSED(집행 전 기간 종료로 소멸) · SUPERSEDED(통지 중 긴급 집행으로 대체)")
            AdminSuspensionStatus status,
            @Schema(description = "NOTICE: 제17조① 호수 — ART17_1_LAW(법령 위반) · ART17_2_IP_DEFECT(지식재산권 침해·중대 하자) · "
                    + "ART17_3_BREACH(중대 의무 불이행) · ART17_4_DISPUTE(분쟁 심화·신용 훼손)", nullable = true)
            SuspensionReasonClause reasonClause,
            @Schema(description = "EMERGENCY: 제17조③ 사유 — CONSUMER_HARM(소비자 위해 방지) · AUTHORITY_ORDER(행정·사법기관의 명령) · "
                    + "DAMAGE_SURGE(피해 급증 우려)", nullable = true)
            EmergencySuspensionReason emergencyReason,
            @Schema(description = "운영자가 쓴 상세 사유 — B4i · B7a 「상세 사유」") String noticeBody,
            @Schema(description = "통지 시각") LocalDateTime noticedAt,
            @Schema(description = "집행 예정 일시 — NOTICE만", nullable = true) LocalDateTime executeScheduledAt,
            @Schema(description = "소명 기한 — NOTICE만. 이 시각까지 소명을 제출할 수 있다", nullable = true) LocalDateTime appealDeadlineAt,
            @Schema(description = "철회 시각", nullable = true) LocalDateTime withdrawnAt,
            @Schema(description = "철회 사유 — 운영자가 브랜드에 전달하는 글", nullable = true) String withdrawReason,
            @Schema(description = "집행 시각", nullable = true) LocalDateTime executedAt,
            @Schema(description = "제출한 소명 — 미제출이면 null", nullable = true) Appeal appeal
    ) {
    }

    public record Appeal(
            @Schema(description = "소명 내용") String content,
            @Schema(description = "제출 시각") LocalDateTime submittedAt,
            @Schema(description = "업로드가 확인된 증빙 첨부 — 다운로드 URL은 싣지 않는다") List<AppealAttachment> attachments
    ) {
    }

    public record AppealAttachment(
            @Schema(description = "첨부 id") Long attachmentId,
            @Schema(description = "원본 파일명", example = "시험성적서.pdf") String originalName,
            @Schema(description = "MIME 타입", example = "application/pdf") String contentType,
            @Schema(description = "파일 크기(바이트)", example = "812345") long sizeBytes
    ) {
    }

    public record Closure(
            @Schema(description = "종결 유형 — COMPLETED(기간 종료) · EARLY_CLOSED(조기 마감) · SUSPENDED(중단)") GroupBuyCloseType closeType,
            @Schema(description = "실제 종결 시각") LocalDateTime endedAt,
            @Schema(description = "REQUEST(요청 승인) · ADMIN_NOTICE(사전 통지 집행) · ADMIN_EMERGENCY(긴급) — 기간 완주는 null",
                    nullable = true) ClosureSource source,
            @Schema(description = "종결을 만든 요청 — B7 「내 요청 사유」. source = REQUEST일 때만", nullable = true) Requester requester,
            @Schema(description = "운영자 승인 사유 — source = REQUEST일 때만", nullable = true) String decisionReason
    ) {
    }

    public enum ClosureSource { REQUEST, ADMIN_NOTICE, ADMIN_EMERGENCY }

    public record Requester(
            @Schema(description = "요청자 — SELLER · CREATOR") GroupBuyActorType type,
            @Schema(description = "브랜드명 또는 쇼룸명") String name,
            @Schema(description = "사유 코드") String reasonCode,
            @Schema(description = "사유 문구 — 알 수 없는 코드는 null", nullable = true) String reasonLabel,
            @Schema(description = "브랜드가 낸 요청일 때만 — 인플루언서 메모는 내리지 않는다", nullable = true) String memo,
            @Schema(description = "요청 시각") LocalDateTime requestedAt
    ) {
    }

    public record AfterEnd(
            @Schema(description = "**폐기(2026-10-06) — 항상 null.** 계약 이행 확인이 없어졌다. 종료 후 화면은 orderClosure 로 그린다", nullable = true, deprecated = true) Fulfillment fulfillment,
            @Schema(description = "열린 이슈 — 없으면 null", nullable = true) OpenIssue openIssue,
            @Schema(description = "종료 +30일 — 어드민 정산 지연 감시 기준. ENDED에서만", nullable = true) LocalDateTime settlementWatchAt,
            @Schema(description = "정산(이체) 완료 시각 — SETTLED에서만", nullable = true) LocalDateTime settledAt
    ) {
    }

    public record Fulfillment(
            @Schema(description = "브랜드가 확인한 것 — 인플루언서의 콘텐츠 의무. 확인 전이면 null", nullable = true) FulfillmentCheck mine,
            @Schema(description = "인플루언서가 확인한 것 — 브랜드의 의무. 확인 전이면 null", nullable = true) FulfillmentCheck theirs,
            @Schema(description = "확인 기한 — 종료 시각 + 설정 일수(기본 3일). 자동 이행이 꺼져 있는 동안은 표시값이다", nullable = true) LocalDateTime dueAt,
            @Schema(description = "기한까지 답하지 않으면 이행으로 처리되는가 — 현재 false. false면 「놔두면 이행」 문구를 쓰면 안 된다")
            boolean autoConfirmOnTimeout,
            @Schema(description = "정산 보류 — 미이행이 있고 양측 합의 종결 전") boolean onHold,
            @Schema(description = "미이행 3자 스레드", nullable = true) Long threadId,
            @Schema(description = "미이행 합의 종결 시각 — 보류 해제", nullable = true) LocalDateTime resolvedAt
    ) {
    }

    public record FulfillmentCheck(
            @Schema(description = "FULFILLED(이행) · UNFULFILLED(미이행)") FulfillmentResult result,
            @Schema(description = "미이행 사유 — UNFULFILLED일 때만", nullable = true) String reason,
            @Schema(description = "확인 시각") LocalDateTime checkedAt,
            @Schema(description = "기한 경과 자동 이행 여부") boolean auto
    ) {
    }

    public record OpenIssue(
            @Schema(description = "이슈 id") Long issueId,
            @Schema(description = "CONTENT_FULFILLMENT · TERMS_INTERPRETATION · SETTLEMENT_AMOUNT · ETC") GroupBuyIssueType type,
            @Schema(description = "개설 측 — SELLER(브랜드) · CREATOR(인플루언서) · ADMIN(운영자)") GroupBuyActorType openerType,
            @Schema(description = "개설 시각") LocalDateTime openedAt,
            @Schema(description = "이슈 스레드 id", nullable = true) Long threadId,
            @Schema(description = "답변 대기 — 스레드의 마지막 글을 개설 측이 썼으면 true(B5e 「답변 대기」). "
                    + "스레드나 글이 없으면 null", nullable = true)
            Boolean awaitingReply
    ) {
    }

    /**
     * 버튼 판정 — 조건이 상태 × 요청 × 통지 × 게시물 × 시각으로 갈린다. 실행 API가 같은 판정을 다시 호출해
     * 409로 막으므로 버튼과 집행이 어긋날 수 없다(설계서 4-5).
     */
    @Schema(description = "버튼 노출 판정 — 서버가 내려준다. 실행 API가 같은 판정으로 409를 내므로 FE가 조건을 복제하지 않는다. "
            + "「요청 차단」= 검토 중 중단·조기 마감 요청 있음 ∨ 중단 예정 ∨ 게시물 숨김 중")
    public record Permissions(
            @Schema(description = "[확보 완료] — PREPARING ∧ 미확인") boolean canConfirmStock,
            @Schema(description = "[기간 연장] — IN_PROGRESS ∧ 연장 요청 이력 없음 ∧ 종료 12시간 전 ∧ 요청 차단 아님")
            boolean canRequestExtension,
            @Schema(description = "[조기 마감 요청] — IN_PROGRESS ∧ 요청 차단 아님") boolean canRequestEarlyClose,
            @Schema(description = "[공구 중단 요청] — READY 또는 IN_PROGRESS ∧ 요청 차단 아님") boolean canRequestSuspension,
            @Schema(description = "[소명 제출] — SUSPENSION_SCHEDULED ∧ 통지 중 ∧ 미제출 ∧ 소명 기한 이내") boolean canSubmitAppeal,
            @Schema(description = "[이슈 스레드 열기] — (ENDED ∨ 브랜드가 요청하지 않은 SUSPENDED) ∧ 열린 이슈 없음") boolean canOpenIssue,
            @Schema(description = "[이행 확인] — **폐기(2026-10-06) · 항상 false**", deprecated = true) boolean canCheckFulfillment,
            @Schema(description = "[스레드 열기] — 인플루언서와 PAIR 스레드가 있음(counterparty.pairThreadId != null)") boolean canOpenPairThread
    ) {
    }

    public record Navigation(
            @Schema(description = "목록 기준 이전 공구 id", example = "52", nullable = true) Long prevGroupBuyId,
            @Schema(description = "목록 기준 다음 공구 id", example = "38", nullable = true) Long nextGroupBuyId) {
    }

    public record HistoryEntry(
            @Schema(description = "이벤트 — 예: CREATED · STOCK_CONFIRMED · OPEN_APPROVED · OPENED · EXTENSION_REQUESTED · "
                    + "SUSPENSION_NOTICED · APPEAL_SUBMITTED · ENDED · FULFILLMENT_CONFIRMED", example = "STOCK_CONFIRMED")
            GroupBuyEventType eventType,
            @Schema(description = "SELLER · CREATOR · ADMIN · SYSTEM — 운영자 호칭은 FE가 고른다") GroupBuyActorType actorType,
            @Schema(description = "브랜드명·쇼룸명 스냅샷 — 운영자·시스템은 null", nullable = true) String actorDisplayName,
            @Schema(description = "부가 문구 — 예: 「7일 · 수요 증가」, 「크림 300개 · 세럼 200개」", nullable = true) String detail,
            @Schema(description = "발생 시각") LocalDateTime occurredAt
    ) {
    }
}
