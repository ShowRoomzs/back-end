package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

/** 증빙 문서 상태(44 어드민 설계서 1-1 · 5-1) — 유형별로 쓰는 부분집합이 다르다. */
@Getter
@RequiredArgsConstructor
public enum TaxDocumentStatus {
    PENDING_INPUT("입력 대기"),
    SUBMITTED("확인 대기"),
    VERIFIED("확인 완료"),
    REJECTED("반려"),
    PENDING_ISSUE("발행 대기"),
    ISSUED("발행 완료"),
    GENERATED("생성 완료");

    private final String label;

    /** 처리 끝 — 07a 증빙 탭에서 뺀다. */
    public static final Set<TaxDocumentStatus> DONE = EnumSet.of(VERIFIED, ISSUED, GENERATED);
    /** 인플루언서가 입력할 수 있다 — 첫 입력 · 반려 뒤 재입력. */
    public static final Set<TaxDocumentStatus> OPEN_FOR_INPUT = EnumSet.of(PENDING_INPUT, REJECTED);
}
