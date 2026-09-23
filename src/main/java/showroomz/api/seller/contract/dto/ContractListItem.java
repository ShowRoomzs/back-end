package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.ContractStatusTone;

import java.time.LocalDateTime;

/** 파트너 계약 목록 행(A1). */
@Schema(description = "계약 목록 행")
public record ContractListItem(

        @Schema(description = "계약 ID", example = "128")
        Long contractId,

        @Schema(description = "계약번호 — 검토 요청 전에는 null(설계서 1-7)", example = "CTR-20260813-001", nullable = true)
        String contractNumber,

        @Schema(description = "공구명 — 작성중이면 null. 서버는 (공구명 미입력) 같은 표시 문구를 지어내지 않는다. "
                + "서버가 가짜 값을 만들면 검색·정렬이 그 값에 걸린다",
                example = "가을 앰플 신제품 공구", nullable = true)
        String title,

        @Schema(description = "계약 상대 표시명(쇼룸명) — 미선택이면 null", example = "글로우_지민", nullable = true)
        String counterpartyName,

        @Schema(description = "상품 항목 수", example = "2")
        long itemCount,

        @Schema(description = "공구 시작 일시", nullable = true)
        LocalDateTime startAt,

        @Schema(description = "공구 종료 일시", nullable = true)
        LocalDateTime endAt,

        @Schema(description = "생성일시")
        LocalDateTime createdAt,

        @Schema(description = "상태 — 탭 묶음이 아니라 9종 개별 값 그대로다(§26-1)", example = "REVIEW_PENDING")
        ContractStatus status,

        @Schema(description = "상태 라벨 — 세 서피스가 같은 문구를 쓰도록 서버가 내린다", example = "검토 대기")
        String statusLabel,

        @Schema(description = "상태 배지 색 — FE 3개가 각자 매핑표를 들면 어긋나므로 서버가 내린다(§25-2 원칙 ③)",
                example = "INFO")
        ContractStatusTone statusTone,

        @Schema(description = "행 클릭 시 진입 모드 — EDIT(작성중·검토 반려) / VIEW(나머지). "
                + "FE가 상태표를 들고 다시 판정하지 않도록 서버가 내린다(§26-1)", example = "VIEW")
        EntryMode entryMode
) {

    public enum EntryMode { EDIT, VIEW }

    public static ContractListItem of(Contract contract, long itemCount) {
        ContractStatus status = contract.getStatus();
        return new ContractListItem(
                contract.getId(),
                contract.getContractNumber(),
                contract.getTitle(),
                contract.getCreator() == null ? null : contract.getCreator().getShowroomName(),
                itemCount,
                contract.getGroupBuyStartAt(),
                contract.getGroupBuyEndAt(),
                contract.getCreatedAt(),
                status,
                status.getLabel(),
                status.getTone(),
                status.isEditable() ? EntryMode.EDIT : EntryMode.VIEW
        );
    }
}
