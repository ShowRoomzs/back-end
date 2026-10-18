package showroomz.api.admin.changerequest.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.entity.ChangeRequestItem;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.SettlementPartnerSyncService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.LocalDateTime;

/**
 * 브랜드 변경 요청(M1 사업자 정보 · M2 정산 계좌)의 승인 반영 · 결과 메일(§16-4 · §7-2). 값은 {@link Seller}/{@link Market}에 옮긴다 — 브랜드명
 * 중복 재검사 · 통장 사본 교체 · PG 셀러 계좌 동기화까지 여기서 한다(정합 계획서 R3-2). 인플루언서 건은
 * {@link CreatorBusinessTypeChangeApplier}가 맡는다.
 */
@Component
public class BrandChangeRequestApplier implements ChangeRequestApplier {

    private final ObjectProvider<SettlementPartnerSyncService> partnerSync;
    private final MarketRepository marketRepository;
    private final MailService mailService;

    public BrandChangeRequestApplier(ObjectProvider<SettlementPartnerSyncService> partnerSync,
                                     MarketRepository marketRepository, MailService mailService) {
        this.partnerSync = partnerSync;
        this.marketRepository = marketRepository;
        this.mailService = mailService;
    }

    @Override
    public ChangeRequesterType requesterType() {
        return ChangeRequesterType.BRAND;
    }

    @Override
    public void apply(ChangeRequest request, LocalDateTime now) {
        if (request.isCreatorRequest()) {
            throw new IllegalArgumentException("브랜드 요청이 아니다: " + request.getType());
        }
        // 접수 뒤 다른 브랜드가 같은 이름을 썼다 — 승인 시점에 다시 본다.
        request.getItems().stream()
                .filter(item -> item.getFieldKey() == ChangeRequestField.MARKET_NAME)
                .findFirst()
                .filter(item -> marketRepository.existsByMarketNameAndSellerStatusNotRejected(item.getRequestedValue(),
                        SellerStatus.REJECTED))
                .ifPresent(item -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_MARKET_NAME);
                });
        Market market = request.getMarket();
        Seller seller = market.getSeller();
        for (ChangeRequestItem item : request.getItems()) {
            applyField(item.getFieldKey(), item.getRequestedValue(), seller, market);
        }

        // §7-2 M2 승인 시 통장 사본 교체 — 계좌가 바뀌었는데 옛 사본이 남으면 다음 정산 대조가 어긋난다.
        if (request.getType() == ChangeRequestType.SETTLEMENT_ACCOUNT) {
            seller.setBankbookImageUrl(request.getEvidenceFileUrl());
            // PG 셀러 계좌도 즉시 맞춘다(토스 설계서 4-3) — 외부 호출은 커밋 뒤 · 실패해도 승인은 끝난다.
            Long marketId = market.getId();
            AfterCommit.run(() -> partnerSync.ifAvailable(sync -> sync.onBrandAccountChanged(marketId)));
        }
    }

    @Override
    public void notifyApproved(ChangeRequest request) {
        mailService.sendChangeRequestApprovedEmail(request.getMarket().getSeller().getEmail(), request.getRequestCode(),
                String.join(", ", request.changedFieldLabels()), request.getProcessedAt());
    }

    @Override
    public void notifyRejected(ChangeRequest request, ChangeRequestRejectReason reason, String detail) {
        mailService.sendChangeRequestRejectedEmail(request.getMarket().getSeller().getEmail(), request.getRequestCode(),
                request.getProcessedAt(), reason.getDescription(), detail);
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
            case CREATOR_BUSINESS_TYPE, CREATOR_BUSINESS_REG_NUMBER, CREATOR_RESIDENT_NUMBER ->
                    throw new IllegalArgumentException("브랜드 요청에 인플루언서 항목: " + field);
        }
    }
}
