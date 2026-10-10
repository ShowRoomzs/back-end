package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 증빙 문서 유형(44 어드민 설계서 0-9 · 5-1) — 라벨 · 발행 방향을 enum 이 든다. */
@Getter
@RequiredArgsConstructor
public enum TaxDocumentType {
    WITHHOLDING_RECEIPT("원천징수영수증", "플랫폼 → 인플루언서"),
    CREATOR_TAX_INVOICE("인플루언서 세금계산서", "인플루언서 → 플랫폼"),
    BRAND_TAX_INVOICE("브랜드 세금계산서", "플랫폼 → 브랜드"),
    BRAND_TAX_INVOICE_CREDIT("수정세금계산서(차감)", "플랫폼 → 브랜드");

    private final String label;
    private final String direction;

    public boolean isBrandInvoice() {
        return this == BRAND_TAX_INVOICE || this == BRAND_TAX_INVOICE_CREDIT;
    }
}
