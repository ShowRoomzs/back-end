package showroomz.api.admin.changerequest.service;

import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;

import java.util.List;

/**
 * 어드민 변경 요청 상세에서 <b>요청자 유형마다 다른 부분</b>(정합 계획서 R3-1) — 공통부(코드 · 상태 · SLA · 경과 · 이전/다음 · 처리 이력)는
 * {@code AdminChangeRequestService}가 붙인다. 브랜드 건은 대조 · 예금주 확인, 인플루언서 건은 비교표 · 승인 영향이 더 있다.
 *
 * @param brandName     브랜드 건만 — 인플루언서 건은 null
 * @param marketId      브랜드 건만
 * @param requesterName 브랜드 담당자 이름 스냅샷 — 인플루언서 건은 본인이라 null
 */
public record ChangeRequestDetailParts(
        String brandName,
        Long marketId,
        String requesterName,
        List<AdminChangeRequestDto.DiffRow> diff,
        List<String> changedFieldLabels,
        AdminChangeRequestDto.Evidence evidence,
        List<AdminChangeRequestDto.ReferenceItem> referenceItems,
        AdminChangeRequestDto.HolderCheck holderCheck,
        List<AdminChangeRequestDto.CompareItem> compareItems,
        AdminChangeRequestDto.ApprovalImpact approvalImpact) {
}
