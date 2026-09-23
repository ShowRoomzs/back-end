package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
 * 계약 상세 — 상세 화면 13종(B2a·B3c·B3d·B4·B4a·B4b·B4c·B5·B5a·B5b·B6·B7·B8)이 이 응답 하나를 쓴다.
 * 화면 분기는 FE가 값으로 고른다(설계서 4-5).
 */
@Schema(description = "계약 상세")
public record ContractDetailResponse(

        @Schema(description = "계약 ID", example = "128")
        Long contractId,

        @Schema(description = "계약번호 — 검토 요청 전에는 null", example = "CTR-20260813-001", nullable = true)
        String contractNumber,

        @Schema(description = "공구명 — 작성중이면 null", example = "가을 앰플 신제품 공구", nullable = true)
        String title,

        @Schema(description = "상태(9종)", example = "SIGNING")
        ContractStatus status,

        @Schema(description = "상태 라벨", example = "서명 진행중")
        String statusLabel,

        @Schema(description = "상태 배지 색", example = "INFO")
        ContractStatusTone statusTone,

        @Schema(description = "낙관적 락 버전 — 임시저장(PUT)에 그대로 되돌려 보낸다(설계서 3-4)", example = "3")
        Long version,

        @Schema(description = "재작성 출처 계약 ID — 작성 화면 상단이 출처를 밝힌다(§26-5)", nullable = true)
        Long sourceContractId,

        Counterparty counterparty,
        Period period,
        List<Item> items,
        FixedFee fixedFee,
        Content content,
        Review review,
        Signature signature,
        Settlement settlement,
        Closure closure,
        GroupBuy groupBuy,
        List<Document> documents,
        Permissions permissions,
        List<HistoryEntry> history
) {

    @Schema(description = "계약 상대")
    public record Counterparty(
            @Schema(description = "인플루언서 ID — 작성중 미선택이면 null", nullable = true) Long creatorId,
            @Schema(description = "쇼룸명", example = "글로우_지민", nullable = true) String showroomName,
            @Schema(description = "연결 ID — 「스레드 열기」 딥링크의 출처", nullable = true) Long connectionId,
            @Schema(description = "스레드 경유로 고정된 상대인지 — true면 변경할 수 없다(§25-5-1)") boolean fixed
    ) {
    }

    @Schema(description = "공구 기간")
    public record Period(
            @Schema(nullable = true) LocalDateTime startAt,
            @Schema(nullable = true) LocalDateTime endAt,
            @Schema(description = "일수 — 시작·종료 일자 양끝 포함", example = "17", nullable = true) Integer days
    ) {
    }

    @Schema(description = "계약 상품 항목 — 상품명·정가는 계약 시점의 스냅샷이다(설계서 0-5)")
    public record Item(
            Long contractItemId,
            @Schema(description = "상품 ID — 공구 생성·정산 귀속용 참조", nullable = true) Long productId,
            @Schema(description = "상품명(스냅샷)", nullable = true) String productName,
            @Schema(description = "정가(스냅샷)", nullable = true) Integer regularPrice,
            @Schema(nullable = true) Integer groupBuyPrice,
            @Schema(description = "리워드율(%) — 정산이 이 값을 그대로 쓴다", example = "15.0", nullable = true)
            BigDecimal rewardRate,
            @Schema(description = "1개당 예상 리워드(원) — 저장하지 않는 파생값. 1원 단위 버림(설계서 1-5)",
                    example = "4200", nullable = true)
            Long unitReward,
            @Schema(nullable = true) Integer minQuantity
    ) {
    }

    @Schema(description = "고정 지급비")
    public record FixedFee(
            @Schema(nullable = true) Integer amount,
            @Schema(nullable = true) FixedFeeTrigger trigger,
            @Schema(description = "지급 시점 라벨", example = "공구 게시물 등록 후", nullable = true) String triggerLabel,
            @Schema(description = "고지 확인 체크 시각", nullable = true) LocalDateTime noticeAgreedAt,
            @Schema(description = "지급 완료 기록 시각", nullable = true) LocalDateTime paidAt,
            @Schema(description = "지급 의무가 살아 있는지 — 종결 3종이면 false. 화면이 「지급 의무 소멸」로 바뀌는 근거")
            boolean obligationAlive
    ) {
    }

    @Schema(description = "콘텐츠 의무 · 2차 활용 · 비고")
    public record Content(
            @Schema(nullable = true) Integer feedCount,
            @Schema(nullable = true) Integer reelsCount,
            @Schema(nullable = true) Integer storyCount,
            @Schema(description = "게시 완료 기한", nullable = true) LocalDate dueDate,
            @Schema(nullable = true) Boolean secondaryUseAllowed,
            @Schema(nullable = true) SecondaryUsePeriodType secondaryUsePeriodType,
            @Schema(description = "FIXED일 때의 개월 수", nullable = true) Integer secondaryUseMonths,
            @Schema(description = "브랜드 사전 검수", nullable = true) Boolean brandPreReview,
            @Schema(nullable = true) String note
    ) {
    }

    @Schema(description = "검토")
    public record Review(
            @Schema(description = "검토 요청 일시 — 리드타임 D+7 판정의 기준일", nullable = true) LocalDateTime requestedAt,
            @Schema(nullable = true) LocalDateTime approvedAt,
            @Schema(nullable = true) LocalDateTime rejectedAt,
            @Schema(nullable = true) RejectReason rejectReason
    ) {
        @Schema(description = "반려 사유 2단(§28-4)")
        public record RejectReason(String code, String detail) {
        }
    }

    @Schema(description = "서명 — 값은 전부 어드민이 모두싸인에서 옮겨 적은 것이다(§25-3)")
    public record Signature(
            @Schema(nullable = true) LocalDateTime requestedAt,
            @Schema(description = "서명 기한 — 만료 판단의 유일한 기준", nullable = true) LocalDateTime deadlineAt,
            @Schema(nullable = true) LocalDateTime brandSignedAt,
            @Schema(nullable = true) LocalDateTime creatorSignedAt,
            @Schema(description = "기준 시각 — 화면 「N 기준」. 사람 손으로 들어온 값이 언제 기준인지 밝힌다",
                    nullable = true) LocalDateTime asOf,
            @Schema(description = "상대가 계약서를 열람했는지. B7의 「계약서 열람 기록 없음」이 이 값으로 그려진다 — "
                    + "거절은 상대 메모가 있지만 만료는 없어 그 공백을 이 사실로 메운다")
            boolean counterpartyViewed
    ) {
    }

    @Schema(description = "정산 조건 요약 — 브랜드 입력이 아니라 자동 표시다")
    public record Settlement(
            @Schema(description = "플랫폼 수수료율(%)", example = "2") Integer platformFeeRate,
            @Schema(description = "PG 결제 수수료율(%) — 자문 회신 전이라 항상 null이다. "
                    + "0으로 내리면 화면이 「0%」로 읽어 수수료가 없다는 뜻이 되어버린다",
                    nullable = true) Integer pgFeeRate,
            @Schema(description = "원천징수 구분 — 상대 계정 정보 기반 자동 전환(§25-5-6). 상대 미선택이면 null",
                    nullable = true) WithholdingType withholdingType,
            @Schema(nullable = true) String withholdingLabel
    ) {
    }

    @Schema(description = "종결 — 거절·만료·취소 공통")
    public record Closure(
            @Schema(nullable = true) LocalDateTime closedAt,
            @Schema(description = "종결 주체", nullable = true) ContractActorType actorType,
            @Schema(description = "만료는 사유가 없어 null이다", nullable = true) String reasonCode,
            @Schema(nullable = true) String reasonLabel,
            @Schema(description = "상대에게 전달되는 메모", nullable = true) String memo
    ) {
    }

    @Schema(description = "공구 생성 게이트 — 계약 1건 = 공구 1건(설계서 1-8)")
    public record GroupBuy(
            @Schema(description = "생성된 공구 ID", nullable = true) Long groupBuyId,
            @Schema(description = "지금 공구를 만들 수 있는지 — 체결완료이고 아직 공구가 없을 때만 true")
            boolean canCreate
    ) {
    }

    @Schema(description = "체결 문서")
    public record Document(
            ContractDocumentType type,
            String typeLabel,
            @Schema(description = "다운로드 URL") String downloadUrl
    ) {
    }

    /**
     * 버튼 노출 조건이 상태 × 서명 조합 × 지급 여부로 갈린다. FE가 이 판정을 복제하면
     * 서버 허용 집합과 어긋나는 버튼이 생기므로 서버 판정을 한 번 내려준다(설계서 4-5).
     *
     * <p>{@code canRequestReview}는 <b>상태상 허용되는지</b>만 뜻한다. 필수 미입력으로 인한 버튼 비활성은
     * 에러 문구 없이 FE가 폼으로 판정하거나 {@code POST /{id}/validate}가 돌려주는
     * {@code canSubmit}으로 가린다(§25-6 · 설계서 2-5).
     */
    @Schema(description = "버튼 노출 판정 — 서버가 내려준다")
    public record Permissions(
            boolean canEdit,
            boolean canDelete,
            boolean canRequestReview,
            boolean canCancelRequest,
            boolean canCancel,
            boolean canRequestResend,
            boolean canRecordPayment,
            boolean canCreateGroupBuy,
            boolean canDuplicate
    ) {
    }

    @Schema(description = "이력 1건 — append-only")
    public record HistoryEntry(
            ContractEventType eventType,
            @Schema(description = "주체. 운영자 호칭(「어드민」/「운영자」/실명)은 읽는 서피스가 고른다(§25-9)")
            ContractActorType actorType,
            @Schema(description = "브랜드명·쇼룸명 스냅샷 — 운영자 주체는 null이다", nullable = true)
            String actorDisplayName,
            @Schema(nullable = true) String detail,
            LocalDateTime occurredAt
    ) {
    }
}
