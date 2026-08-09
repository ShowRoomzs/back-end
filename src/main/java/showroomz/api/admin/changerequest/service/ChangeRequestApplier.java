package showroomz.api.admin.changerequest.service;

import org.springframework.stereotype.Component;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.entity.BrandChangeRequestItem;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;

/**
 * 승인된 변경 요청의 값을 {@link Seller}/{@link Market}에 반영한다(§16-4).
 * 전체 승인 또는 전체 반려뿐 — 부분 승인은 없다. 여기서는 필드 매핑만 다루고,
 * 브랜드명 중복 재검사·통장 사본 교체 등 승인 절차의 나머지는 호출부(AdminChangeRequestService)가 맡는다.
 */
@Component
public class ChangeRequestApplier {

    public void apply(BrandChangeRequest request) {
        Market market = request.getMarket();
        Seller seller = market.getSeller();
        for (BrandChangeRequestItem item : request.getItems()) {
            applyField(item.getFieldKey(), item.getRequestedValue(), seller, market);
        }

        // §7-2 M2 승인 시 통장 사본 교체 — 계좌가 바뀌었는데 옛 사본이 남으면 다음 정산 대조가 어긋난다.
        if (request.getType() == ChangeRequestType.SETTLEMENT_ACCOUNT) {
            seller.setBankbookImageUrl(request.getEvidenceFileUrl());
        }
    }

    private void applyField(ChangeRequestField field, String requestedValue, Seller seller, Market market) {
        switch (field) {
            case MARKET_NAME -> market.setMarketName(requestedValue);
            case REPRESENTATIVE_NAME -> seller.setRepresentativeName(requestedValue);
            case COMPANY_NAME -> seller.setCompanyName(requestedValue);
            case BUSINESS_CONDITION -> seller.setBusinessCondition(requestedValue);
            case BUSINESS_ADDRESS -> {
                // 요청값 전체를 businessAddress에 넣고 detailAddress는 정규화(§A-1)
                seller.setBusinessAddress(requestedValue);
                seller.setDetailAddress(null);
            }
            case MAIL_ORDER_REG_NUMBER -> seller.setMailOrderRegNumber(requestedValue);
            // BANK_CODE는 요청 접수 시점에 이미 은행명으로 변환해 저장했다(§1-3) — 그대로 대입한다.
            case BANK_CODE -> seller.setBankName(requestedValue);
            case ACCOUNT_NUMBER -> seller.setAccountNumber(requestedValue);
            case ACCOUNT_HOLDER -> seller.setAccountHolder(requestedValue);
        }
    }
}
