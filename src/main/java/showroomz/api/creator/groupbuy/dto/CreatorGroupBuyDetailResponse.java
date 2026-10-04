package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.EmergencySuspensionReason;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.domain.groupbuy.type.SuspensionReasonClause;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 스튜디오 공구 상세 — B1~B13 + 모달 C1~C7의 배경 화면이 <b>이 응답 하나</b>를 쓴다(31 설계 4-1). 분기는 FE가 값으로 고른다.
 *
 * <p><b>파트너 응답과 DTO를 공유하지 않는다</b>(31 설계 0-5). 공유하면 언젠가 파트너 전용 필드가 하나 붙고 그게 스튜디오로
 * 샌다. 덜어낸 것 — 최소 준비 물량 · 비고 · 직권 중단 소명 · 상대가 운영자에게 쓴 메모 · 고정 지급비 지급 신고 · version.
 */
@Schema(description = "스튜디오 공구 상세 — 상세 화면과 실행 API 응답이 함께 쓴다. 조건 밖의 블록은 null이다")
public record CreatorGroupBuyDetailResponse(
        @Schema(description = "공구 요약 — 항상") Summary groupBuy,
        @Schema(description = "기간 — 항상") Timeline timeline,
        @Schema(description = "상대 브랜드 — 항상") Brand brand,
        @Schema(description = "원본 계약 — 항상") ContractRef contract,
        @Schema(description = "공구 상품 = 계약 상품 전부 — 항상") List<Item> items,
        @Schema(description = "고정 지급비 — 항상(계약에 없으면 내부 필드가 null)") FixedFee fixedFee,
        @Schema(description = "「내가 받는 금액」 — 비종결(준비중·준비완료·진행중·중단 예정)에서만. 종결 3종은 null", nullable = true)
        Payout payout,
        @Schema(description = "준비 게이트 3개 — PREPARING·READY에서만", nullable = true) Readiness readiness,
        @Schema(description = "공구 게시물 — 항상(미작성이면 status만 NOT_WRITTEN이고 disclosureText 외 나머지는 null)") Post post,
        @Schema(description = "판매 실적 — 진행중·중단 예정(LIVE) · 종료(PROVISIONAL · 잠정) · 정산완료(SETTLED) · "
                + "중단(AT_SUSPENSION). 준비중·준비완료는 null. 판매 모듈이 없으면 항상 null — 0이 아니다", nullable = true)
        Sales sales,
        @Schema(description = "주문 종결 건수 — 진행중·중단 예정·종료·중단에서만. 판매 모듈이 없으면 null(0이 아니다)", nullable = true)
        OrderClosure orderClosure,
        @Schema(description = "기간 연장 요청 — 브랜드가 요청한 적이 없으면 null. 응답 후에도(수락·거절·만료) 남는다", nullable = true)
        Extension extension,
        @Schema(description = "검토 중(PENDING) 중단·조기 마감 요청 — 요청자 무관. 있으면 중단 요청이 막힌다", nullable = true)
        ActiveRequest activeRequest,
        @Schema(description = "진행 중인 직권 중단 통지 — 중단 예정(SUSPENSION_SCHEDULED)에서만", nullable = true)
        AdminSuspension adminSuspension,
        @Schema(description = "종결 정보 — 종결 3종(ENDED·SETTLED·SUSPENDED)에서만", nullable = true) Closure closure,
        @Schema(description = "정산 — 정산완료(SETTLED)에서만", nullable = true) Settlement settlement,
        @Schema(description = "종료 후 이행 확인 — 종료·정산완료에서만(중단은 null)", nullable = true) AfterEnd afterEnd,
        @Schema(description = "버튼 노출 판정 — 항상") Permissions permissions,
        @Schema(description = "이력 — 최신순(발생 시각 내림차순)") List<HistoryEntry> history,
        @Schema(description = "목록 이웃 — 목록 조건(tab·keyword·sort)이 쿼리로 오지 않으면 둘 다 null") Navigation navigation
) {

    public record Summary(
            @Schema(example = "41") Long groupBuyId,
            @Schema(description = "공구번호 — GB-생성일(YYYYMMDD)-순번", example = "GB-20260803-041") String groupBuyNumber,
            @Schema(description = "공구명 — 계약에서 읽는다", example = "여름 수분 세럼 공구") String title,
            @Schema(description = "공구 상태 7종 — PREPARING(준비중) · READY(준비완료) · IN_PROGRESS(진행중) · "
                    + "SUSPENSION_SCHEDULED(중단 예정) · ENDED(종료) · SETTLED(정산완료) · SUSPENDED(중단)", example = "IN_PROGRESS")
            GroupBuyStatus status,
            @Schema(description = "상태 배지 문구", example = "진행중") String statusLabel,
            @Schema(description = "상태 배지 색 — NEUTRAL · INFO · WARNING · SUCCESS · DANGER", example = "SUCCESS")
            GroupBuyTone statusTone,
            @Schema(description = "공구 생성 시각 = 계약 체결 시각", example = "2026-08-03T15:30:00") LocalDateTime createdAt,
            @Schema(description = "준비 게이트 3개가 모두 채워진 시각", example = "2026-08-07T10:15:00", nullable = true)
            LocalDateTime readyAt,
            @Schema(description = "스케줄러가 실제로 공구를 연 시각", example = "2026-08-14T10:00:32", nullable = true)
            LocalDateTime openedAt,
            @Schema(description = "실제 종결 시각 — 기간 종료면 종료 예정 시각, 조기 마감·중단이면 판정 시각", nullable = true)
            LocalDateTime endedAt,
            @Schema(description = "종결 유형 — COMPLETED(기간 종료) · EARLY_CLOSED(조기 마감) · SUSPENDED(중단)", nullable = true)
            GroupBuyCloseType closeType,
            @Schema(description = "종결 유형 문구", example = "기간 종료", nullable = true) String closeTypeLabel,
            @Schema(description = "정산 완료 시각", nullable = true) LocalDateTime settledAt
    ) {
    }

    @Schema(description = "기간 — 일수는 서버 now(Asia/Seoul) 기준 · 양끝 포함 일자 계산")
    public record Timeline(
            @Schema(description = "시작 일시 — 계약 값 · 불변", example = "2026-08-14T10:00:00") LocalDateTime startAt,
            @Schema(description = "현재 종료 예정 — 연장 수락이 반영된 값", example = "2026-08-20T23:55:00") LocalDateTime endAt,
            @Schema(description = "원래 종료 예정(계약)", example = "2026-08-20T23:55:00") LocalDateTime originalEndAt,
            @Schema(description = "총 기간(일) — 시작일 ~ 현재 종료일, 양끝 포함", example = "7") int totalDays,
            @Schema(description = "진행 N일차 — 시작 전이면 0, 종결 후에는 종결 시각까지", example = "5") int elapsedDays,
            @Schema(description = "시작까지 N일 — 시작 후면 null", example = "9", nullable = true) Integer daysUntilStart,
            @Schema(description = "종료까지 N일 — 판매 중(진행중·중단 예정)일 때만", example = "2", nullable = true) Integer daysUntilEnd,
            @Schema(description = "시작 시각이 지났는데 아직 준비중 — 배너 문구가 「시작 시각이 지났습니다」로 바뀐다") boolean startOverdue
    ) {
    }

    public record Brand(
            @Schema(description = "브랜드(마켓) id", example = "7") Long marketId,
            @Schema(description = "브랜드명", example = "글로우랩") String name,
            @Schema(description = "「스레드 열기」 대상 PAIR 스레드 — 연결이 끊겼으면 null", example = "305", nullable = true)
            Long pairThreadId
    ) {
    }

    @Schema(description = "「계약서 보기」 · 「게시 완료 기한」")
    public record ContractRef(
            @Schema(example = "12") Long contractId,
            @Schema(description = "계약번호", example = "CTR-20260728-012") String contractNumber,
            @Schema(description = "체결 시각", example = "2026-08-03T15:30:00") LocalDateTime concludedAt,
            @Schema(description = "콘텐츠 게시 완료 기한", example = "2026-08-18", nullable = true) LocalDate contentDueDate) {
    }

    @Schema(description = "공구 상품 = 계약 상품 전부. 정가·최소 준비 물량은 싣지 않는다 — 브랜드 소관이다(B1)")
    public record Item(
            @Schema(example = "101", nullable = true) Long productId,
            @Schema(example = "글로우 수분 세럼 50ml") String productName,
            @Schema(description = "공구가(원)", example = "28000") Integer groupBuyPrice,
            @Schema(description = "내 리워드율(%)", example = "15.0") BigDecimal myRewardRate,
            @Schema(description = "개당 리워드(원) = ⌊공구가 × 리워드율 ÷ 100⌋ — RewardCalculator.calcUnitReward(계약·파트너·정산과 같은 메서드)",
                    example = "4200")
            Long unitReward,
            @Schema(description = "옵션별 판매가 — 최소 준비 물량은 싣지 않는다(브랜드 소관)")
            List<ItemOption> options
    ) {
    }

    @Schema(description = "공구 상품 옵션 — 판매가 = 공구가 + 옵션가. 리워드는 상품 공구가 기준이라 옵션마다 같다")
    public record ItemOption(
            @Schema(example = "301", nullable = true) Long variantId,
            @Schema(description = "옵션명(계약 스냅샷)", example = "2개 세트", nullable = true) String variantName,
            @Schema(description = "옵션 판매가(원)", example = "50000", nullable = true) Integer salePrice
    ) {
    }

    public record FixedFee(
            @Schema(description = "고정 지급비(원) — 없으면 null", example = "300000", nullable = true) Integer amount,
            @Schema(description = "지급 시점 — POST_REGISTERED(공구 게시물 등록 후) · GROUP_BUY_ENDED(공구 종료 후) · "
                    + "SETTLEMENT_COMPLETED(정산 완료 후)", example = "POST_REGISTERED", nullable = true)
            FixedFeeTrigger trigger,
            @Schema(example = "공구 게시물 등록 후", nullable = true) String triggerLabel,
            @Schema(description = "3서피스 문자 단위 동일 표준 표기 — FE가 조립하지 않는다. 고정 지급비가 없으면 null",
                    example = "고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급", nullable = true)
            String displayText
    ) {
    }

    @Schema(description = "「내가 받는 금액」 — 공제 전 금액이다. 공제·실지급액은 정산 관리 소관")
    public record Payout(
            @Schema(description = "고정 지급비(원) — 브랜드 직접 지급", example = "300000", nullable = true) Integer fixedFeeAmount,
            @Schema(description = "판매 리워드(잠정) — 판매 중이고 판매 모듈이 값을 줄 때만(현재 항상 null)", nullable = true)
            SalesReward salesReward,
            @Schema(description = "플랫폼 지급 보증 여부 — 항상 false. FE는 이 값으로 미보증 고지(§29-9)를 붙인다", example = "false")
            boolean platformGuaranteed,
            @Schema(description = "미보증 고지의 「이슈 스레드에서 운영자 중재」가 갈 곳") DisputeChannel disputeChannel
    ) {
    }

    public record SalesReward(
            @Schema(description = "잠정 리워드(원)", example = "184800") long amount,
            @Schema(description = "항상 LIVE", example = "LIVE") SalesBasis basis) {
    }

    public record DisputeChannel(@Schema(description = "PAIR 스레드 id — 연결이 끊겼으면 null", example = "305", nullable = true)
                                 Long threadId) {
    }

    public record Readiness(
            @Schema(description = "게이트 3개 — ① STOCK_CONFIRMED · ② POST_SUBMITTED · ③ OPEN_APPROVED 순서 고정") List<Gate> gates,
            @Schema(description = "등록 마감일 — 제출 후 SLA 영업일 안에 승인이 나도 시작일 전날까지 끝나는 가장 늦은 날(서버 역산)",
                    example = "2026-08-10")
            LocalDate registrationDeadline,
            @Schema(description = "등록 마감일이 지났고 게이트 ②가 아직이다") boolean registrationOverdue,
            @Schema(description = "오픈 승인 SLA(영업일)", example = "3") int reviewSlaBusinessDays
    ) {
    }

    public record Gate(
            @Schema(description = "STOCK_CONFIRMED(① 브랜드 물량 확인) · POST_SUBMITTED(② 내 게시물 제출) · OPEN_APPROVED(③ 운영자 오픈 승인)",
                    example = "POST_SUBMITTED")
            GateKey key,
            @Schema(description = "게이트를 채우는 주체 — ① SELLER · ② CREATOR · ③ ADMIN", example = "CREATOR") GroupBuyActorType actorType,
            @Schema(description = "DONE(완료) · MY_TURN(내 차례 — ②만, 게시물 없음·작성중·반려) · WAITING(대기) · IN_REVIEW(③ 운영자 검토 중)",
                    example = "MY_TURN")
            GateState state,
            @Schema(description = "MY_TURN일 때만 WARNING, 나머지는 NEUTRAL", example = "WARNING") GroupBuyTone tone,
            @Schema(description = "DONE일 때만 — ① 확인 시각 · ② 제출 시각 · ③ 승인 시각. 수량은 싣지 않는다", nullable = true)
            LocalDateTime doneAt
    ) {
    }

    public enum GateKey { STOCK_CONFIRMED, POST_SUBMITTED, OPEN_APPROVED }

    public enum GateState { DONE, MY_TURN, WAITING, IN_REVIEW }

    public record Post(
            @Schema(description = "게시물 상태 8종(파생값) — NOT_WRITTEN(미작성) · WRITING(작성중) · PENDING_APPROVAL(승인대기) · "
                    + "REJECTED(반려) · SCHEDULED(예약 — 승인됐고 시작 전) · EXPOSED(노출중) · HIDDEN(숨김) · CLOSED(종료 — 종결 3종 공통)",
                    example = "EXPOSED")
            GroupBuyPostStatus status,
            @Schema(description = "게시물 상태 배지 문구", example = "노출중") String statusLabel,
            @Schema(description = "게시물 상태 배지 색", example = "SUCCESS") GroupBuyTone statusTone,
            @Schema(description = "제목 — 40자", example = "여름 수분 세럼, 제가 쓰던 그 조합", nullable = true) String title,
            @Schema(description = "본문 — 2,000자", example = "건조한 여름에도 속당김 없이…", nullable = true) String content,
            @Schema(description = "대가관계 표시 — 저장하지 않고 서버가 브랜드명으로 조립한다. 게시물이 없어도 미리보기용으로 내린다",
                    example = "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다")
            String disclosureText,
            @Schema(description = "판매자 정보(상호·사업자번호·교환·반품)는 소비자 화면에 자동 표기된다 — 값은 싣지 않는다. 항상 true")
            boolean sellerInfoAutoAttached,
            @Schema(description = "검토 요청(제출) 시각", example = "2026-08-06T14:02:00", nullable = true) LocalDateTime submittedAt,
            @Schema(description = "승인대기일 때 예상 승인일 — 제출일 + SLA 영업일(제출일은 세지 않는다)", example = "2026-08-11", nullable = true)
            LocalDate expectedReviewDate,
            @Schema(description = "운영자 검토(승인·반려) 시각", nullable = true) LocalDateTime reviewedAt,
            @Schema(description = "노출 시작 — 공구 오픈 시각", nullable = true) LocalDateTime openedAt,
            @Schema(description = "노출 종료 — 공구 종결 시각(종결 3종에서만)", nullable = true) LocalDateTime closedAt,
            @Schema(description = "승인 후 마지막 수정 시각", nullable = true) LocalDateTime lastEditedAt,
            @Schema(description = "반려 — 반려 상태일 때만", nullable = true) Rejection rejection,
            @Schema(description = "숨김 — 숨김 중일 때만(종결 후에는 null)", nullable = true) Hidden hidden
    ) {
    }

    public record Rejection(
            @Schema(description = "반려 사유 코드 — AD_EFFECT_ASSERTION(효과 단정) · AD_MEDICAL_CLAIM(의료적 효능 표현) · "
                    + "AD_SUPERLATIVE(최저가·최상급 표현) · CONTRACT_PRODUCT_MISMATCH(계약과 다른 상품 구성) · "
                    + "CONTRACT_PRICE_MISMATCH(계약과 다른 가격 표기) · DISCLOSURE_DAMAGED(대가관계 표시 훼손) · ETC(기타)",
                    example = "AD_EFFECT_ASSERTION")
            String code,
            @Schema(description = "운영자가 인플루언서에게 쓴 설명", nullable = true) String detail,
            @Schema(nullable = true) LocalDateTime rejectedAt) {
    }

    public record Hidden(
            @Schema(description = "숨김 사유 코드 — AD_EFFECT_ASSERTION · AD_MEDICAL_CLAIM · AD_SUPERLATIVE · DISCLOSURE_DAMAGED · "
                    + "CONTRACT_MISMATCH(계약과 다른 상품·가격 기재) · FALSE_INFORMATION(사실과 다른 정보) · ETC",
                    example = "AD_MEDICAL_CLAIM")
            String code,
            @Schema(description = "운영자 설명", nullable = true) String detail,
            @Schema(description = "숨김 시각", example = "2026-08-16T15:20:00") LocalDateTime hiddenAt,
            @Schema(description = "숨김 N일차 — 양끝 포함", example = "2") int hiddenDays
    ) {
    }

    public record Sales(
            @Schema(description = "LIVE · PROVISIONAL(종료 · 확정 시 변동) · SETTLED · AT_SUSPENSION") SalesBasis basis,
            @Schema(description = "주문 수") int orderCount,
            @Schema(description = "종결 중 구매확정 — B8 「구매확정 308건」. 파트너·어드민 orderClosure와 같은 값. 판매 모듈이 모르면 null",
                    example = "308", nullable = true)
            Integer purchaseConfirmedCount,
            @Schema(description = "종결 중 환불(결제 후 취소) — B8 「환불 4건 반영」. 판매 모듈이 모르면 null",
                    example = "4", nullable = true)
            Integer refundedCount,
            @Schema(description = "상품별 판매 수량") List<ItemQuantity> itemQuantities,
            @Schema(description = "판매액(원)") long amount,
            @Schema(description = "내 리워드(원) — 항목별 수량 × 개당 리워드의 합") long myReward,
            @Schema(description = "숨김 이후 주문 수 — 숨김 중일 때만. 판매 모듈이 모르면 null(0이 아니다)", nullable = true)
            Long ordersSinceHidden
    ) {
    }

    public enum SalesBasis { LIVE, PROVISIONAL, SETTLED, AT_SUSPENSION }

    public record ItemQuantity(Long productId, int quantity) {
    }

    public record OrderClosure(
            @Schema(description = "전체 주문 수") int totalCount,
            @Schema(description = "종결(배송 완료·취소·반품 완료 등)된 주문 수") int closedCount,
            @Schema(description = "미종결 내역") Unclosed unclosed) {
    }

    @Schema(description = "미종결이 어디에 걸려 있나 — B7 「배송 처리 대기 18 · 반품 처리중 6」")
    public record Unclosed(
            int total,
            @Schema(nullable = true) Integer awaitingShipment,
            @Schema(nullable = true) Integer inReturnOrExchange
    ) {
    }

    public record Extension(
            @Schema(description = "PENDING(응답 대기) · ACCEPTED(수락) · REJECTED(거절) · EXPIRED(종료 시각까지 무응답 — 변경 없이 종료)",
                    example = "PENDING")
            ExtensionRequestStatus status,
            @Schema(description = "연장 일수", example = "7") int days,
            @Schema(description = "브랜드가 쓴 연장 사유", example = "재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", nullable = true)
            String reason,
            @Schema(description = "연장 전 종료 시각", example = "2026-08-20T23:55:00") LocalDateTime beforeEndAt,
            @Schema(description = "연장 후 종료 시각 — 시각(시·분)은 그대로이고 날짜만 늘어난다", example = "2026-08-27T23:55:00")
            LocalDateTime afterEndAt,
            @Schema(description = "연장 전 총 일수", example = "7") int beforeTotalDays,
            @Schema(description = "연장 후 총 일수 — 30일 이하", example = "14") int afterTotalDays,
            @Schema(description = "요청 시각", example = "2026-08-17T16:40:00") LocalDateTime requestedAt,
            @Schema(description = "응답 기한 = 현재 종료 시각. 대기 중일 때만", example = "2026-08-20T23:55:00", nullable = true)
            LocalDateTime respondDeadlineAt,
            @Schema(description = "응답(또는 만료) 시각", nullable = true) LocalDateTime respondedAt,
            @Schema(description = "CREATOR(수락·거절) / SYSTEM(기간 만료)", nullable = true) GroupBuyActorType responseActorType,
            @Schema(description = "거절 사유 코드 — NEXT_SCHEDULE_BOOKED · CONTENT_PLAN_MISMATCH · TERMS_RENEGOTIATION · ETC", nullable = true)
            String rejectReasonCode,
            @Schema(description = "거절 사유 문구", example = "다음 일정이 잡혀 있음", nullable = true) String rejectReasonLabel,
            @Schema(description = "거절 메모 — 브랜드에게도 보인다", nullable = true) String rejectMemo
    ) {
    }

    public record ActiveRequest(
            @Schema(description = "SUSPEND(공구 중단) · EARLY_CLOSE(조기 마감 — 브랜드만 발의)", example = "SUSPEND") ChangeRequestType type,
            @Schema(description = "요청자 — SELLER · CREATOR", example = "CREATOR") GroupBuyActorType requesterType,
            @Schema(description = "내가 낸 요청인가") boolean mine,
            @Schema(description = "사유 코드 — 요청자·유형별 코드 집합이 다르다", example = "DELIVERY_FAILURE") String reasonCode,
            @Schema(description = "사유 문구", example = "배송 지연 · 미발송이 계속됨", nullable = true) String reasonLabel,
            @Schema(description = "내 요청일 때만 — 상대의 메모는 운영자에게 쓴 글이라 내리지 않는다", nullable = true) String memo,
            @Schema(description = "요청 시각", example = "2026-08-17T20:10:00") LocalDateTime requestedAt
    ) {
    }

    @Schema(description = "직권 중단 사전 통지 — 소명 기한·소명 내용은 싣지 않는다(브랜드↔운영자 절차)")
    public record AdminSuspension(
            @Schema(description = "NOTICE(사전 통지) · EMERGENCY(긴급)", example = "NOTICE") AdminSuspensionKind kind,
            @Schema(description = "제17조① 호수 — ART17_1_LAW(법령 위반) · ART17_2_IP_DEFECT(지식재산권 침해·중대 하자) · "
                    + "ART17_3_BREACH(중대 의무 불이행) · ART17_4_DISPUTE(분쟁 심화·신용 훼손). 표시 문구는 FE가 호수로 고른다",
                    example = "ART17_2_IP_DEFECT", nullable = true)
            SuspensionReasonClause reasonClause,
            @Schema(description = "통지 본문 — 운영자가 양측에 쓴 글") String noticeBody,
            @Schema(description = "통지 시각", example = "2026-08-17T18:00:00") LocalDateTime noticedAt,
            @Schema(description = "집행 예정 시각", example = "2026-08-20T18:00:00", nullable = true) LocalDateTime executeScheduledAt,
            @Schema(description = "집행까지 N영업일 — B13 「D-3」", example = "2", nullable = true) Integer businessDaysUntilExecution
    ) {
    }

    public record Closure(
            @Schema(description = "COMPLETED(기간 종료) · EARLY_CLOSED(조기 마감) · SUSPENDED(중단)", example = "COMPLETED")
            GroupBuyCloseType closeType,
            @Schema(description = "종결 시각") LocalDateTime endedAt,
            @Schema(description = "REQUEST(요청 승인) · ADMIN_NOTICE(사전 통지 후 집행) · ADMIN_EMERGENCY(긴급 직권 중단) — 기간 완주는 null",
                    nullable = true)
            ClosureSource source,
            @Schema(description = "종결을 만든 요청 — source = REQUEST일 때만. 메모는 싣지 않는다", nullable = true) Requester requester,
            @Schema(description = "운영자가 양측에 쓴 결론 — 요청 승인일 때만", nullable = true) String decisionReason,
            @Schema(description = "판정(요청 승인) 또는 집행 시각", nullable = true) LocalDateTime decidedAt,
            @Schema(description = "직권 중단 근거 — source = ADMIN_*일 때만", nullable = true) AdminBasis adminBasis
    ) {
    }

    public enum ClosureSource { REQUEST, ADMIN_NOTICE, ADMIN_EMERGENCY }

    public record Requester(
            @Schema(description = "요청자 — SELLER · CREATOR", example = "CREATOR") GroupBuyActorType type,
            @Schema(description = "내가 낸 요청인가") boolean mine,
            @Schema(description = "요청자 이름 — 쇼룸명 또는 브랜드명", example = "민지의 쇼룸") String name,
            @Schema(example = "DELIVERY_FAILURE") String reasonCode,
            @Schema(example = "배송 지연 · 미발송이 계속됨", nullable = true) String reasonLabel,
            LocalDateTime requestedAt
    ) {
    }

    public record AdminBasis(
            @Schema(description = "NOTICE · EMERGENCY") AdminSuspensionKind kind,
            @Schema(description = "사전 통지(NOTICE)의 제17조① 호수", nullable = true) SuspensionReasonClause reasonClause,
            @Schema(description = "긴급(EMERGENCY) 사유 — CONSUMER_HARM(소비자 위해 방지) · AUTHORITY_ORDER(행정·사법기관의 명령) · "
                    + "DAMAGE_SURGE(피해 급증 우려)", nullable = true)
            EmergencySuspensionReason emergencyReason,
            @Schema(description = "통지 본문") String body
    ) {
    }

    public record Settlement(
            @Schema(description = "정산 완료 시각", example = "2026-09-05T15:00:00") LocalDateTime settledAt,
            @Schema(description = "고정 지급비(원) — 계약 금액. 실제 수령 여부와 무관", example = "300000", nullable = true)
            Integer fixedFeeAmount,
            @Schema(description = "정산 모듈이 확정한 리워드(원) — 모듈 연동 전이면 null", nullable = true) Long confirmedReward,
            @Schema(description = "공제 전 합계 = 고정 지급비 + 확정 리워드. 확정 리워드를 모르면 null", nullable = true)
            Long totalBeforeDeduction
    ) {
    }

    public record AfterEnd(@Schema(description = "계약 이행 확인") Fulfillment fulfillment) {
    }

    public record Fulfillment(
            @Schema(description = "내가 브랜드의 이행을 확인한 결과 — 미확인이면 null", nullable = true) FulfillmentCheck mine,
            @Schema(description = "브랜드가 내 이행을 확인한 결과 — 미확인이면 null", nullable = true) FulfillmentCheck theirs,
            @Schema(description = "내가 확인할 대상 — 브랜드의 의무(주문 배송 · 고정 지급비 지급)") Target myTarget,
            @Schema(description = "상대가 확인할 대상 — 나의 콘텐츠 의무") Target theirTarget,
            @Schema(description = "확인 기한 = 종결 시각(endedAt) + 3일 — 기간 종료·조기 마감 모두", example = "2026-08-23T23:55:00", nullable = true)
            LocalDateTime dueAt,
            @Schema(description = "기한까지 답하지 않으면 이행으로 처리되는가 — 현재 false. false면 「놔두면 이행」 문구를 쓰면 안 된다")
            boolean autoConfirmOnTimeout,
            @Schema(description = "정산 보류 — 미이행이 있고 양측 합의 종결 전") boolean onHold,
            @Schema(description = "미이행 3자 스레드", nullable = true) Long threadId,
            @Schema(description = "정산 보류 해제 시각", nullable = true) LocalDateTime resolvedAt
    ) {
    }

    public record FulfillmentCheck(
            @Schema(description = "FULFILLED(이행) · UNFULFILLED(미이행)", example = "FULFILLED") FulfillmentResult result,
            @Schema(description = "미이행 사유", nullable = true) String reason,
            @Schema(description = "확인 시각", example = "2026-08-22T10:30:00") LocalDateTime checkedAt,
            @Schema(description = "기한 경과 자동 이행 여부") boolean auto
    ) {
    }

    public record Target(
            @Schema(description = "BRAND · CREATOR", example = "BRAND") Party party,
            @Schema(description = "의무 목록 — BRAND: ORDER_DELIVERY(주문 배송) · FIXED_FEE_PAYMENT(고정 지급비 지급 — 계약에 있을 때만) / "
                    + "CREATOR: SHOWROOM_POST(쇼룸 공구 게시물) · FEED · REELS · STORY(건수 0인 의무는 뺀다)")
            List<Duty> duties,
            @Schema(description = "콘텐츠 의무 수 — 인플루언서 대상일 때만", nullable = true) ContentCounts counts
    ) {
    }

    public enum Party { BRAND, CREATOR }

    public enum Duty { ORDER_DELIVERY, FIXED_FEE_PAYMENT, SHOWROOM_POST, FEED, REELS, STORY }

    public record ContentCounts(
            @Schema(description = "인스타그램 피드", example = "1") int feed,
            @Schema(description = "릴스", example = "0") int reels,
            @Schema(description = "스토리", example = "3") int story) {
    }

    /** 버튼 판정 — 실행 API가 같은 판정으로 409를 내므로 FE가 복제하지 않는다(31 설계 4-8). */
    @Schema(description = "버튼 노출 판정 — 서버가 내려준다. 실행 API가 같은 판정으로 409를 낸다")
    public record Permissions(
            @Schema(description = "[임시저장] · [등록하고 검토 요청] — PREPARING ∧ 게시물 없음·작성중·반려") boolean canWritePost,
            @Schema(description = "[게시물 수정](승인 후) — 게시물 승인됨 ∧ READY·IN_PROGRESS(숨김 포함). 중단 예정·종결은 잠김")
            boolean canEditPost,
            @Schema(description = "[수락] · [거절](연장) — 연장 PENDING ∧ IN_PROGRESS ∧ 현재 시각 < 종료 시각") boolean canRespondExtension,
            @Schema(description = "[공구 중단 요청] — IN_PROGRESS ∧ 검토 중 요청 없음 ∧ 게시물 숨김 아님") boolean canRequestSuspension,
            @Schema(description = "[이행 확인] — ENDED ∧ 내 확인 전(기한 경과 무관)") boolean canCheckFulfillment,
            @Schema(description = "[스레드 열기] — 브랜드와의 PAIR 스레드가 있을 때") boolean canOpenPairThread
    ) {
    }

    public record HistoryEntry(
            @Schema(description = "이벤트 — 스튜디오 화이트리스트만 내린다(브랜드 소명 APPEAL_SUBMITTED 제외)", example = "EXTENSION_REQUESTED")
            GroupBuyEventType eventType,
            @Schema(description = "SELLER · CREATOR · ADMIN · SYSTEM — 운영자 호칭(「운영자」)은 FE가 고른다", example = "SELLER")
            GroupBuyActorType actorType,
            @Schema(description = "브랜드명·쇼룸명 스냅샷 — 운영자·시스템은 null", example = "글로우랩", nullable = true) String actorDisplayName,
            @Schema(description = "이벤트별로 내릴지가 정해진다 — 브랜드에게 쓴 문장은 내리지 않는다. 중단·조기 마감 요청은 사유 라벨만",
                    example = "7일 · 재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", nullable = true)
            String detail,
            @Schema(description = "발생 시각", example = "2026-08-17T16:40:00") LocalDateTime occurredAt
    ) {
    }

    public record Navigation(
            @Schema(description = "목록 기준 이전 공구 id", example = "52", nullable = true) Long prevGroupBuyId,
            @Schema(description = "목록 기준 다음 공구 id", example = "38", nullable = true) Long nextGroupBuyId) {
    }
}
