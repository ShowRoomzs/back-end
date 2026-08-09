package showroomz.domain.changerequest.service;

import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;

/**
 * {@link ChangeRequestField}별 현재값 조회의 단일 지점. 요청 생성 시 스냅샷, 필드 목록(M1·M2 모달),
 * 어드민 대조표의 미변경 행이 모두 이 로직을 공유해야 값 대응이 어긋나지 않는다.
 */
public final class ChangeRequestFieldResolver {

    private ChangeRequestFieldResolver() {
    }

    public static String currentValue(ChangeRequestField field, Seller seller, Market market) {
        return switch (field) {
            case MARKET_NAME -> market.getMarketName();
            case REPRESENTATIVE_NAME -> seller.getRepresentativeName();
            case COMPANY_NAME -> seller.getCompanyName();
            case BUSINESS_CONDITION -> seller.getBusinessCondition();
            case BUSINESS_ADDRESS -> combineAddress(seller.getBusinessAddress(), seller.getDetailAddress());
            case MAIL_ORDER_REG_NUMBER -> seller.getMailOrderRegNumber();
            case BANK_CODE -> seller.getBankName();
            case ACCOUNT_NUMBER -> seller.getAccountNumber();
            case ACCOUNT_HOLDER -> seller.getAccountHolder();
        };
    }

    /** §A-1 — 사업장 주소는 자유 입력 단일 문자열로 다룬다(카카오 주소 API 아님). */
    public static String combineAddress(String address, String detailAddress) {
        if (address == null) {
            return null;
        }
        if (detailAddress == null || detailAddress.isBlank()) {
            return address;
        }
        return address + ", " + detailAddress;
    }
}
