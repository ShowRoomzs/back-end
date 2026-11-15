package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사전 검수 초안 제출의 회신 상태(수정요청 B-13 · 계약서 제8조). */
@Getter
@RequiredArgsConstructor
public enum ContractDraftSubmissionStatus {
    PENDING("회신 대기"),
    APPROVED("승인"),
    REVISION_REQUESTED("수정 요청"),
    /** 수령 후 3영업일 무회신 — 시스템이 승인 간주를 기록했다. */
    DEEMED_APPROVED("승인 간주");

    private final String label;
}
