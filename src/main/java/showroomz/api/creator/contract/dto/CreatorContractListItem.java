package showroomz.api.creator.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.api.creator.contract.type.CreatorDeadlineDisplayType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractDeadlinePolicy;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.ContractStatusTone;

import java.time.LocalDateTime;

/**
 * 스튜디오 계약 목록 행(시안 S1).
 *
 * <p><b>파트너의 {@code ContractListItem}과 DTO를 공유하지 않는다</b>(설계서 4-2의 리스크).
 * 공유하면 언젠가 파트너 전용 필드가 하나 붙고 그게 스튜디오로 샌다.
 *
 * <p>덜어낸 것:
 * <ul>
 *   <li>{@code createdAt} — §27-1 #3 「생성일은 브랜드의 사정이다」</li>
 *   <li>{@code entryMode} — 스튜디오에 작성 모드가 없다. 진입은 항상 상세다(§27-1 #2)</li>
 * </ul>
 */
@Schema(description = "스튜디오 계약 목록 행")
public record CreatorContractListItem(

        @Schema(description = "계약 ID", example = "128")
        Long contractId,

        @Schema(description = "계약번호 — 도착한 계약은 반드시 값이 있다", example = "CTR-20260813-001")
        String contractNumber,

        @Schema(description = "공구명 — 도착한 계약은 반드시 값이 있다. 공구명은 검토 요청 시 필수 검증을 "
                + "통과했으므로 파트너 목록의 (공구명 미입력) 문제가 스튜디오에 도달하지 않는다",
                example = "여름 수분 세럼 공구")
        String title,

        @Schema(description = "브랜드명 — 계약에 스냅샷 컬럼이 없어 market 조인으로 읽는다", example = "퓨어랩")
        String brandName,

        @Schema(description = "상품 항목 수", example = "2")
        long itemCount,

        @Schema(description = "공구 시작 일시", nullable = true)
        LocalDateTime startAt,

        @Schema(description = "공구 종료 일시", nullable = true)
        LocalDateTime endAt,

        @Schema(description = "받은 일시 — 서명 요청 발송 시각이 곧 도착 시각이다(설계서 0-6). "
                + "별도 컬럼을 두지 않는다")
        LocalDateTime receivedAt,

        @Schema(description = "상태 — 6종 중 하나. 탭 묶음이 아니라 개별 값이다", example = "SIGNING")
        ContractStatus status,

        @Schema(description = "상태 라벨", example = "서명 진행중")
        String statusLabel,

        @Schema(description = "상태 배지 색", example = "INFO")
        ContractStatusTone statusTone,

        @Schema(description = "「내 서명 기한」 열의 판정 결과")
        Deadline deadline
) {

    /**
     * 「내 서명 기한」 열 — 서버가 <b>표시 종류</b>를 내린다(설계서 2-2).
     *
     * <p>{@code tone}만 서버가 주고 문구는 FE가 고른다. 표시 문자열이 아니라 판정 결과를 내린다.
     */
    @Schema(description = "「내 서명 기한」 열 판정")
    public record Deadline(

            @Schema(description = "표시 종류", example = "DEADLINE")
            CreatorDeadlineDisplayType type,

            @Schema(description = "기한 일시 — type=DEADLINE일 때만 값이 있다", nullable = true)
            LocalDateTime deadlineAt,

            @Schema(description = "경고색 여부. **type=DEADLINE일 때만 WARNING이 될 수 있다** — "
                    + "기한 임박은 내 서명이 남아 있을 때만 의미가 있다. "
                    + "기한이 지나면 만료(중립 종결)이지 부정 결과 확정이 아니므로 "
                    + "**이 열에 DANGER는 존재하지 않는다**",
                    example = "WARNING")
            ContractStatusTone tone
    ) {
    }

    public static CreatorContractListItem of(Contract contract, long itemCount, LocalDateTime now) {
        ContractStatus status = contract.getStatus();
        return new CreatorContractListItem(
                contract.getId(),
                contract.getContractNumber(),
                contract.getTitle(),
                contract.getMarket().getMarketName(),
                itemCount,
                contract.getGroupBuyStartAt(),
                contract.getGroupBuyEndAt(),
                contract.getSignatureRequestedAt(),
                status,
                status.getLabel(),
                status.getTone(),
                deadlineOf(contract, now));
    }

    /** 설계서 2-2의 여섯 갈래 표를 그대로 옮긴 곳 — 이 판정이 사는 유일한 자리다. */
    private static Deadline deadlineOf(Contract contract, LocalDateTime now) {
        return switch (contract.getStatus()) {
            case SIGNING -> contract.getCreatorSignedAt() != null
                    ? new Deadline(CreatorDeadlineDisplayType.MY_SIGNED, null, ContractStatusTone.NEUTRAL)
                    : new Deadline(
                            CreatorDeadlineDisplayType.DEADLINE,
                            contract.getSignatureDeadlineAt(),
                            ContractDeadlinePolicy.isImminent(contract.getSignatureDeadlineAt(), now)
                                    ? ContractStatusTone.WARNING
                                    : ContractStatusTone.NEUTRAL);
            case CONCLUSION_PENDING ->
                    new Deadline(CreatorDeadlineDisplayType.BOTH_SIGNED, null, ContractStatusTone.NEUTRAL);
            case CONCLUDED ->
                    new Deadline(CreatorDeadlineDisplayType.SIGNED, null, ContractStatusTone.NEUTRAL);
            case EXPIRED ->
                    new Deadline(CreatorDeadlineDisplayType.PASSED, null, ContractStatusTone.NEUTRAL);
            // DECLINED · CANCELED. 나머지 3종은 가시성 판정에서 이미 걸러져 여기 도달하지 않는다.
            default ->
                    new Deadline(CreatorDeadlineDisplayType.NONE, null, ContractStatusTone.NEUTRAL);
        };
    }
}
