package showroomz.domain.product.type;

/**
 * 화장품 개봉 시 청약철회 제한 표시(수정요청 C · 전자상거래법 제17조②③) — 상품별 적용 여부와 브랜드 문구를 합쳐 소비자에게 보일 문장을 정한다.
 * 브랜드 문구가 없으면 기본 문구다. 하자 · 오배송은 제한 대상이 아니라는 단서를 기본 문구에 둔다.
 */
public final class WithdrawalRestrictionPolicy {

    public static final String DEFAULT_NOTICE = "화장품은 포장을 개봉하거나 사용하면 상품 가치가 현저히 줄어 단순 변심 반품(청약철회)이 제한돼요. "
            + "상품 하자 · 오배송은 개봉했어도 반품할 수 있어요.";

    private WithdrawalRestrictionPolicy() {
    }

    /** 표시할 문장 — 제한하지 않는 상품이면 null. */
    public static String noticeOf(boolean restricted, String brandNotice) {
        if (!restricted) {
            return null;
        }
        return brandNotice == null || brandNotice.isBlank() ? DEFAULT_NOTICE : brandNotice.trim();
    }
}
