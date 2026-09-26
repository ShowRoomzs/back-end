package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.creator.contract.type.CreatorFixedFeePaymentState;
import showroomz.api.creator.contract.type.CreatorSettlementTiming;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.ContractStatusTone;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.contract.type.WithholdingType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 스튜디오 계약 상세 — 화면 8종(S3 · S3a · S3b · S3c · S6 · S7 · S8 · S9)이 이 응답 하나를 쓴다.
 *
 * <p>서버가 {@code viewPhase: "S3a"} 같은 값을 만들지 않는다(설계서 4-1). §25-2가 「서명 진행중을
 * 쪼개지 않는다」고 한 이유가 그대로 적용된다 — 조합을 값으로 만들면 조합이 늘 때마다 값이 는다.
 * FE의 분기 근거는 {@code status} · {@code signature.brandSignedAt} · {@code signature.creatorSignedAt} 셋이다.
 *
 * <p><b>파트너의 {@code ContractDetailResponse}와 DTO를 공유하지 않는다</b>(설계서 4-2).
 * 라벨이 아니라 <b>필드 집합 자체</b>가 다르고, 공유하면 브랜드 내부 정보가 샌다.
 *
 * <p>덜어낸 것(설계서 6-1):
 * <ul>
 *   <li>{@code createdAt} — 생성일은 브랜드의 사정이다(§27-1 #3)</li>
 *   <li>{@code review} 블록 전체 — 검토 요청 시각 · <b>반려 사유</b>는 운영자가 브랜드에게만 한 지적이다.
 *       스튜디오에 필요한 {@code reviewApprovedAt} 하나만 {@code stepper}로 옮겼다</li>
 *   <li>{@code warningFlags}(W1~W6) — 운영자 전달용. 인플루언서에게 「이 계약은 리워드율이 높다」를
 *       알릴 근거가 없다</li>
 *   <li>{@code sourceContractId} · {@code fixedFeeNoticeAgreedAt} — 브랜드의 내부 이력</li>
 *   <li>{@code version} — 스튜디오는 계약을 수정하지 않는다</li>
 *   <li>브랜드 쪽 환불·정산 이력 — §27-5 「내 자금이 아니다」</li>
 * </ul>
 */
@Schema(description = "스튜디오 계약 상세")
public record CreatorContractDetailResponse(

        @Schema(description = "계약 ID", example = "128")
        Long contractId,

        @Schema(description = "계약번호", example = "CTR-20260813-001")
        String contractNumber,

        @Schema(description = "공구명", example = "여름 수분 세럼 공구")
        String title,

        @Schema(description = "상태 — 도착한 계약의 6종 중 하나", example = "SIGNING")
        ContractStatus status,

        @Schema(description = "상태 라벨", example = "서명 진행중")
        String statusLabel,

        @Schema(description = "상태 배지 색", example = "INFO")
        ContractStatusTone statusTone,

        @Schema(description = "받은 일시 = 서명 요청 발송 시각(설계서 0-6)")
        LocalDateTime receivedAt,

        Brand brand,
        Period period,
        Stepper stepper,
        Signature signature,

        @Schema(description = "「내가 받는 금액」 — **종결 3종에서는 null**이다(설계서 0-5). "
                + "값을 내리고 FE가 숨기는 방식은, 숨기는 조건을 FE가 틀리면 "
                + "성립하지 않은 계약의 금액이 「내가 받는 금액」으로 보이는 사고가 된다",
                nullable = true)
        Payout payout,

        @Schema(description = "「내가 해야 할 일」 — 종결 3종에서도 **그대로 내리고** "
                + "obligationAlive=false를 함께 준다. 카드는 남기되 「효력 없음」으로 표기하는 것이 §27-6이다")
        Content content,

        List<Item> items,
        FixedFee fixedFee,
        Settlement settlement,
        Closure closure,
        GroupBuy groupBuy,
        List<Document> documents,
        Permissions permissions,

        @Schema(description = "이력 — 화이트리스트 7종만(설계서 6-4). 필터는 쿼리 단계에서 걸린다")
        List<HistoryEntry> history,

        Navigation navigation
) {

    @Schema(description = "계약을 보낸 브랜드")
    public record Brand(
            @Schema(example = "31") Long marketId,
            @Schema(example = "퓨어랩") String name,
            @Schema(description = "**connectionId가 아니라 threadId를 내린다**(설계서 8-1). "
                    + "[스레드에서 협의하기]가 유일한 회복 경로인데(§27-1 #5), "
                    + "FE가 connectionId로 스레드를 한 번 더 조회해야 하면 그 한 번이 실패할 수 있다. "
                    + "스레드가 아직 열리지 않았으면 null이다",
                    example = "4012", nullable = true) Long threadId,
            @Schema(description = "연결 상태가 CONNECTED인지 — 시안 S7의 「연결 상태: 연결됨 유지」")
            boolean connected
    ) {
    }

    @Schema(description = "공구 기간")
    public record Period(
            @Schema(nullable = true) LocalDateTime startAt,
            @Schema(nullable = true) LocalDateTime endAt,
            @Schema(description = "일수 — 시작·종료 일자 양끝 포함", example = "17", nullable = true) Integer days
    ) {
    }

    /**
     * 상세 헤더의 4단 스텝퍼.
     *
     * <p>{@code reviewApprovedAt}만 검토 블록에서 건너왔다 — 「운영자 검토 통과」 한 줄에 필요한
     * 값이 그것뿐이고, 반려 사유 같은 나머지는 내리지 않는다(설계서 4-2).
     */
    @Schema(description = "진행 스텝퍼(4단)")
    public record Stepper(
            @Schema(description = "운영자 검토 통과", nullable = true) LocalDateTime reviewApprovedAt,
            @Schema(description = "서명 요청 발송", nullable = true) LocalDateTime signatureRequestedAt,
            @Schema(description = "서명 완료 수(0~2)", example = "1") int signedCount,
            @Schema(description = "체결 완료", nullable = true) LocalDateTime concludedAt
    ) {
    }

    @Schema(description = "서명 현황")
    public record Signature(
            @Schema(description = "내 서명 기한", nullable = true) LocalDateTime deadlineAt,
            @Schema(nullable = true) LocalDateTime brandSignedAt,
            @Schema(nullable = true) LocalDateTime creatorSignedAt,
            @Schema(description = "**「N 기준」 — SIGNING · CONCLUSION_PENDING에서 항상 값이 있다**(설계서 4-5). "
                    + "서명 값은 운영자가 모두싸인을 보고 손으로 옮겨 적는다(§25-3 #2). "
                    + "아직 한 번도 갱신하지 않은 계약은 signature_as_of가 NULL이라 "
                    + "**signature_requested_at으로 대체**해 내린다 — 「발송 시점 기준, 아직 확인 전」이 사실이고, "
                    + "NULL을 내리면 화면이 기준 시각 줄을 통째로 못 그려 "
                    + "「방금 서명했는데 왜 반영이 안 됐지」를 해명할 장치가 사라진다.\n\n"
                    + "**서명 링크는 응답에 없다** — 우리가 갖고 있지 않다(§25-3 #1)",
                    nullable = true) LocalDateTime asOf
    ) {
    }

    /**
     * 「내가 받는 금액」 — <b>스튜디오 전용 블록</b>이다(§27-1 #4). 파트너에는 이 블록이 없다.
     * 같은 값이 파트너센터에서는 내는 돈, 스튜디오에서는 받는 돈이라 라벨만 바꿔서는 성립하지 않는다.
     */
    @Schema(description = "「내가 받는 금액」 — 공제 전 기준")
    public record Payout(
            @Schema(description = "고정 지급비(원)", example = "1200000", nullable = true) Integer fixedFeeAmount,
            @Schema(description = "지급 시점 — 계약서에 기재된 값", nullable = true) FixedFeeTrigger fixedFeeTrigger,
            @Schema(example = "공구 게시물 등록 후", nullable = true) String fixedFeeTriggerLabel,
            @Schema(description = "상품별 판매 리워드율") List<RewardRate> rewardRates,
            @Schema(description = "판매 리워드 정산 시점 — 플랫폼 고정 정책이다", example = "GROUP_BUY_ENDED")
            CreatorSettlementTiming settlementTiming,
            @Schema(description = "**플랫폼이 지급을 보증하지 않는다**(§27-5 · §25-5-4). "
                    + "MVP에서는 항상 false다. 상수를 필드로 내리는 것이 낭비처럼 보이지만, "
                    + "전제가 바뀌면(플랫폼 중개 도입) 이 한 필드로 화면이 갈린다 — "
                    + "문구를 FE에 하드코딩하면 그날 스튜디오·파트너·어드민 세 곳을 고쳐야 한다",
                    example = "false") boolean platformGuaranteed,
            @Schema(description = "미지급 시 갈 곳 — 문구만 두면 인플루언서가 어디로 가야 하는지 모른다")
            DisputeChannel disputeChannel
    ) {

        @Schema(description = "상품별 리워드율")
        public record RewardRate(
                @Schema(example = "수분진정 세럼", nullable = true) String productName,
                @Schema(example = "15.0", nullable = true) BigDecimal rate
        ) {
        }

        @Schema(description = "분쟁 경로 — 이슈 스레드 · 운영자 중재 · 계약서가 근거")
        public record DisputeChannel(
                @Schema(description = "스레드가 없으면 null", example = "4012", nullable = true) Long threadId
        ) {
        }
    }

    @Schema(description = "「내가 해야 할 일」 — 콘텐츠 의무")
    public record Content(
            @Schema(nullable = true) Integer feedCount,
            @Schema(nullable = true) Integer reelsCount,
            @Schema(nullable = true) Integer storyCount,
            @Schema(description = "게시 완료 기한", nullable = true) LocalDate dueDate,
            @Schema(description = "2차 활용 허용", nullable = true) Boolean secondaryUseAllowed,
            @Schema(nullable = true) SecondaryUsePeriodType secondaryUsePeriodType,
            @Schema(nullable = true) Integer secondaryUseMonths,
            @Schema(description = "브랜드 사전 검수 — 초안 확인 기록은 스레드에만 남는다(설계서 미결 #7)",
                    nullable = true) Boolean preReview,
            @Schema(nullable = true) String note,
            @Schema(description = "의무가 살아 있는지 — 종결 3종이면 false. "
                    + "카드는 남기되 「효력 없음」으로 표기한다(§27-6)", example = "true")
            boolean obligationAlive
    ) {
    }

    /**
     * 계약 상품 항목 — <b>필드명을 스튜디오 관점으로 바꾼다</b>(§25-5-3의 스튜디오 라벨 열).
     * {@code rewardRate} → {@code myRewardRate}, {@code minQuantity} → {@code brandSupplyQuantity}.
     * 값은 같지만 관점이 반대라 이름이 같을 수 없다.
     */
    @Schema(description = "계약 상품 항목 — 상품명·정가는 계약 시점 스냅샷이다")
    public record Item(
            Long contractItemId,
            @Schema(nullable = true) Long productId,
            @Schema(nullable = true) String productName,
            @Schema(description = "정가(스냅샷)", nullable = true) Integer regularPrice,
            @Schema(nullable = true) Integer groupBuyPrice,
            @Schema(description = "**내 리워드율**(%) — 정산이 이 값을 그대로 쓴다", example = "15.0", nullable = true)
            BigDecimal myRewardRate,
            @Schema(description = "개당 예상 리워드(원) — 저장하지 않는 파생값. 1원 단위 버림. "
                    + "파트너와 **같은 공유 유틸**을 호출한다 — 같은 계약이 두 화면에서 다른 금액이 되면 안 된다",
                    example = "4200", nullable = true) Long expectedUnitReward,
            @Schema(description = "**브랜드 준비 물량**", example = "300", nullable = true) Integer brandSupplyQuantity
    ) {
    }

    @Schema(description = "고정 지급비 — 「체결완료 = 지급 전」을 못박는 자리(설계서 4-4)")
    public record FixedFee(
            @Schema(example = "1200000", nullable = true) Integer amount,
            @Schema(nullable = true) FixedFeeTrigger trigger,
            @Schema(example = "공구 게시물 등록 후", nullable = true) String triggerLabel,
            @Schema(description = "지급 상태. **fixed_fee_paid_at 원시값은 내리지 않는다**",
                    example = "NOT_YET") CreatorFixedFeePaymentState paymentState
    ) {
    }

    @Schema(description = "정산 조건 요약")
    public record Settlement(
            @Schema(example = "2") int platformFeeRate,
            @Schema(description = "PG 수수료율 — 자문 회신 전이라 null이다. 0으로 내리면 화면이 "
                    + "「0%」로 읽어 수수료가 없다는 뜻이 되어버린다", nullable = true) Integer pgFeeRate,
            @Schema(description = "원천징수 표기 — Creator.businessType에서 파생된다. "
                    + "**실지급액은 계산하지 않는다**(§25-5-6). 스튜디오는 이 값이 파트너보다 중요한데, "
                    + "세무가 확정되기 전에 숫자를 지어내면 그 숫자로 서명을 결정하게 된다",
                    nullable = true) WithholdingType withholdingType,
            @Schema(example = "원천징수 3.3%", nullable = true) String withholdingLabel
    ) {
    }

    /**
     * 종결 정보(설계서 0-5). 세 종결이 같은 컬럼을 쓰고 서버는 블록을 그대로 내린다.
     * 화면 문구의 주체 전환(「내가」 / 「브랜드가」)은 {@code actorType} 하나로 FE가 고른다 —
     * <b>문구를 서버가 짓지 않는다.</b>
     */
    @Schema(description = "종결 정보 — 진행 중이면 필드가 모두 null")
    public record Closure(
            @Schema(nullable = true) LocalDateTime closedAt,
            @Schema(description = "CREATOR=내가 거절 · ADMIN=운영자가 만료·취소(status로 구분). "
                    + "브랜드 취소(SELLER)는 서명 요청 발송 전에만 가능해 스튜디오에 도착하지 않는다", nullable = true)
            ContractActorType actorType,
            @Schema(description = "만료는 사유가 없어 null이다", nullable = true) String reasonCode,
            @Schema(nullable = true) String reasonLabel,
            @Schema(description = "상대가 입력한 메모 — 서버가 가공하지 않는다", nullable = true) String memo
    ) {
    }

    @Schema(description = "연결된 공구")
    public record GroupBuy(
            @Schema(nullable = true) Long groupBuyId,
            @Schema(description = "체결완료인데 아직 공구가 없는 상태 — 시안 S6 「브랜드 생성 대기」. "
                    + "공구는 체결 트랜잭션에서 자동 생성되므로(공구 설계서 0-2) 공구 모듈 이전에 체결돼 "
                    + "백필 전인 계약에서만 true다", example = "false") boolean awaitingBrandCreation
    ) {
    }

    @Schema(description = "계약 문서 — 서명 진행중·체결 처리 대기는 계약서 생성본, 체결완료는 체결 문서 2종")
    public record Document(
            ContractDocumentType documentType,
            @Schema(example = "서명 완료 계약서") String documentTypeLabel,
            String downloadUrl
    ) {
    }

    /**
     * 스튜디오 권한 <b>5종</b>(설계서 4-6). 파트너의 7종과 교집합이 거의 없다.
     *
     * <p>없는 것: {@code canSign}(누를 대상이 없는 버튼은 만들지 않는다 · §25-3 #1) ·
     * {@code canCreateGroupBuy}(공구를 만드는 쪽은 브랜드다) · 수정·삭제·취소 일체.
     */
    @Schema(description = "버튼 노출 판정(5종)")
    public record Permissions(
            @Schema(description = "SIGNING이고 내 서명이 아직 없을 때") boolean canDecline,
            @Schema(description = "canDecline과 조건이 같다 — 둘 다 「내 서명이 아직 남아 있을 때」다. "
                    + "S3b에서 액션이 [스레드에서 협의하기] 하나로 줄어드는 것이 §27-2의 규칙이고, "
                    + "재발송도 이미 서명한 사람에게는 보낼 이유가 없다") boolean canRequestResend,
            @Schema(description = "연결이 살아 있고 스레드가 있으면 **항상 true** — 종결 3종 포함. "
                    + "「회복 경로는 [스레드에서 협의하기] 하나」가 §27-1 #5의 결론이다")
            boolean canOpenThread,
            @Schema(description = "CONCLUDED이고 문서 2종이 있을 때") boolean canDownloadDocuments,
            @Schema(description = "이미 생성된 공구로 **이동**할 뿐이다") boolean canOpenGroupBuy
    ) {
    }

    @Schema(description = "이력 1건")
    public record HistoryEntry(
            @Schema(description = "화이트리스트 7종 + 합성된 연결 성립") ContractEventType eventType,
            @Schema(description = "주체 — **ADMIN의 표시명 「운영자」는 FE가 고른다**. "
                    + "파트너는 같은 값을 「어드민」으로 그린다(§25-9)") ContractActorType actorType,
            @Schema(description = "표시명 스냅샷 — 브랜드명·쇼룸명", nullable = true) String actorDisplayName,
            @Schema(nullable = true) String detail,
            LocalDateTime occurredAt
    ) {
    }

    /**
     * 상세 헤더의 [‹ 이전] [다음 ›](설계서 2-5).
     *
     * <p>두 건은 <b>현재 목록의 정렬·필터 안에서의 이웃</b>이라 상세 API가 목록 조건을 모르면
     * 계산할 수 없다. {@code tab}·{@code keyword}·{@code sort}를 선택 쿼리 파라미터로 받고,
     * 없으면 두 값은 null이며 FE는 버튼을 비활성한다.
     */
    @Schema(description = "이전/다음 이동")
    public record Navigation(
            @Schema(nullable = true) Long prevContractId,
            @Schema(nullable = true) Long nextContractId
    ) {
    }
}
