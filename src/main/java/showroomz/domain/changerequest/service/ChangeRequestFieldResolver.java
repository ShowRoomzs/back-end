package showroomz.domain.changerequest.service;

import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.ResidentRegistrationNumber;
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
            case CREATOR_BUSINESS_TYPE, CREATOR_BUSINESS_REG_NUMBER, CREATOR_RESIDENT_NUMBER, CREATOR_ACCOUNT_BANK,
                 CREATOR_ACCOUNT_NUMBER -> throw new IllegalArgumentException("인플루언서 항목은 브랜드 현재값이 없다: " + field);
        };
    }

    /** 인플루언서 사업자 구분 변경 항목의 현재값(16 설계서 2-3) — 사업자 구분은 코드로 저장한다(표시 라벨은 조회 시). */
    public static String currentValue(ChangeRequestField field, Creator creator) {
        return switch (field) {
            case CREATOR_BUSINESS_TYPE -> creator.getBusinessType() == null ? null : creator.getBusinessType().name();
            case CREATOR_BUSINESS_REG_NUMBER -> creator.getBusinessRegistrationNumber();
            case CREATOR_RESIDENT_NUMBER -> ResidentRegistrationNumber.display(creator.getResidentRegistrationNumberMasked());
            default -> throw new IllegalArgumentException("브랜드 항목은 인플루언서 현재값이 없다: " + field);
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
