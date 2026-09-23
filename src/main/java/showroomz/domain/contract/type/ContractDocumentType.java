package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 체결 문서 2종(§25-3 #3). 교체·삭제 경로를 만들지 않는다. */
@Getter
@RequiredArgsConstructor
public enum ContractDocumentType {
    SIGNED_PDF("서명 완료 계약서"),
    AUDIT_TRAIL("감사 추적 인증서");

    private final String label;
}
