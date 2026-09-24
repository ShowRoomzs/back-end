package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 생성본 및 체결 문서 2종. 체결 문서는 체결 전까지 교체·삭제할 수 있다. */
@Getter
@RequiredArgsConstructor
public enum ContractDocumentType {
    GENERATED_DRAFT("계약서 생성본"),
    SIGNED_PDF("서명 완료 계약서"),
    AUDIT_TRAIL("감사 추적 인증서");

    private final String label;
}
