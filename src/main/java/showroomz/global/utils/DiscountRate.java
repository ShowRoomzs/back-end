package showroomz.global.utils;

/**
 * 할인율(%) — 정가 대비 판매가. C7 상품 상세·C8 장바구니·공구 게시물 상품 행이 <b>이 메서드 하나</b>를 쓴다.
 * 반올림 규칙이 두 벌이면 같은 상품이 게시물에서는 34%, 상세에서는 33%로 보인다(공구 게시물 설계 3절).
 */
public final class DiscountRate {

    private DiscountRate() {
    }

    /** 반올림 정수 — 0~100으로 자른다. 정가가 없거나 0이면 0이다. */
    public static int of(Integer regularPrice, Integer salePrice) {
        if (regularPrice == null || salePrice == null || regularPrice <= 0) {
            return 0;
        }
        double rate = ((double) (regularPrice - salePrice) / regularPrice) * 100.0;
        int rounded = (int) Math.round(rate);
        if (rounded < 0) {
            return 0;
        }
        return Math.min(rounded, 100);
    }
}
