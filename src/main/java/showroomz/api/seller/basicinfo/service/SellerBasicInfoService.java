package showroomz.api.seller.basicinfo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.refreshToken.SellerRefreshToken;
import showroomz.api.seller.auth.refreshToken.SellerRefreshTokenRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.api.seller.basicinfo.SettlementAccountMasker;
import showroomz.api.seller.basicinfo.dto.SellerBasicInfoDto;
import showroomz.api.seller.changerequest.service.BrandChangeRequestService;
import showroomz.domain.changerequest.service.ChangeRequestFieldResolver;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerBasicInfoService {

    private static final DateTimeFormatter NEXT_CHANGEABLE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final BrandChangeRequestService brandChangeRequestService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SellerRefreshTokenRepository sellerRefreshTokenRepository;

    public SellerBasicInfoDto.BusinessInfoResponse getBusinessInfo(String sellerEmail) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();

        return SellerBasicInfoDto.BusinessInfoResponse.builder()
                .brandName(market.getMarketName())
                .brandStatus(market.getStatus())
                .businessType(seller.getBusinessType())
                .marketName(market.getMarketName())
                .representativeName(seller.getRepresentativeName())
                .companyName(seller.getCompanyName())
                .businessRegistrationNumber(seller.getBusinessRegistrationNumber())
                .businessCondition(seller.getBusinessCondition())
                .businessAddress(ChangeRequestFieldResolver.combineAddress(seller.getBusinessAddress(), seller.getDetailAddress()))
                .mailOrderRegNumber(seller.getMailOrderRegNumber())
                .taxEmail(seller.getTaxEmail())
                .brandSiteUrl(market.getBrandSiteUrl())
                .reviewDocuments(buildReviewDocuments(seller))
                .changeRequest(brandChangeRequestService.getBanner(sellerEmail, ChangeRequestType.BUSINESS_INFO))
                .build();
    }

    @Transactional
    public void updateBusinessInfo(String sellerEmail, SellerBasicInfoDto.UpdateBusinessInfoRequest request) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();
        seller.setTaxEmail(request.getTaxEmail());
        market.setBrandSiteUrl(request.getBrandSiteUrl());
    }

    public SellerBasicInfoDto.SettlementInfoResponse getSettlementInfo(String sellerEmail) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();

        return SellerBasicInfoDto.SettlementInfoResponse.builder()
                .bankName(seller.getBankName())
                .maskedAccountNumber(SettlementAccountMasker.mask(seller.getAccountNumber()))
                .accountHolder(seller.getAccountHolder())
                .changeRequest(brandChangeRequestService.getBanner(sellerEmail, ChangeRequestType.SETTLEMENT_ACCOUNT))
                .build();
    }

    public SellerBasicInfoDto.ManagerInfoResponse getManagerInfo(String sellerEmail) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();

        return SellerBasicInfoDto.ManagerInfoResponse.builder()
                .managerName(seller.getName())
                .managerContact(seller.getPhoneNumber())
                .csNumber(market.getCsNumber())
                .returnAddress(SellerBasicInfoDto.ReturnAddress.builder()
                        .recipientName(market.getShippingRecipientName())
                        .recipientContact(market.getShippingContact())
                        .address(market.getShippingAddress())
                        .detailAddress(market.getShippingDetailAddress())
                        .build())
                .build();
    }

    @Transactional
    public void updateManagerInfo(String sellerEmail, SellerBasicInfoDto.UpdateManagerInfoRequest request) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();

        seller.setName(request.getManagerName());
        seller.setPhoneNumber(request.getManagerContact());
        market.setCsNumber(request.getCsNumber());
        market.setShippingRecipientName(request.getRecipientName());
        market.setShippingContact(request.getRecipientContact());
        market.setShippingAddress(request.getAddress());
        market.setShippingDetailAddress(request.getDetailAddress());
    }

    public SellerBasicInfoDto.AccountInfoResponse getAccountInfo(String sellerEmail) {
        Seller seller = getMySeller(sellerEmail);
        return toAccountInfoResponse(seller);
    }

    @Transactional
    public void changePassword(String sellerEmail, SellerBasicInfoDto.ChangePasswordRequest request) {
        Seller seller = getMySeller(sellerEmail);
        if (!passwordEncoder.matches(request.getCurrentPassword(), seller.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_PASSWORD_MISMATCH);
        }
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_CONFIRM_MISMATCH);
        }
        seller.setPassword(passwordEncoder.encode(request.getNewPassword()));
    }

    /** §15-5 로그인 이메일 변경 — 1개월 롤링 제한, 구 이메일 통지, 리프레시 토큰 정리까지 한 트랜잭션에서 처리한다. */
    @Transactional
    public SellerBasicInfoDto.AccountInfoResponse changeEmail(String sellerEmail, SellerBasicInfoDto.ChangeEmailRequest request) {
        Seller seller = getMySeller(sellerEmail);

        if (!passwordEncoder.matches(request.getCurrentPassword(), seller.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_PASSWORD_MISMATCH);
        }

        String newEmail = request.getNewEmail();
        if (newEmail.equalsIgnoreCase(seller.getEmail())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "현재 이메일과 동일합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (seller.getEmailChangedAt() != null) {
            LocalDateTime nextChangeableAt = seller.getEmailChangedAt().plusMonths(1);
            if (now.isBefore(nextChangeableAt)) {
                throw new BusinessException(ErrorCode.EMAIL_CHANGE_LIMIT_EXCEEDED,
                        "로그인 이메일은 월 1회만 변경할 수 있습니다. 다음 변경 가능일은 "
                                + nextChangeableAt.format(NEXT_CHANGEABLE_DATE_FORMAT) + "입니다.");
            }
        }

        if (sellerRepository.existsByEmailAndStatusNotRejected(newEmail, SellerStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL_SIGNUP);
        }

        String oldEmail = seller.getEmail();
        seller.setEmail(newEmail);
        seller.setEmailChangedAt(now);

        mailService.sendLoginEmailChangedNotice(oldEmail, newEmail, now);

        // 이메일이 리프레시 토큰 키다 — 갱신하지 않으면 구 이메일 키로 남아 토큰 갱신이 조용히 깨진다.
        SellerRefreshToken refreshToken = sellerRefreshTokenRepository.findByAdminEmail(oldEmail);
        if (refreshToken != null) {
            sellerRefreshTokenRepository.delete(refreshToken);
        }

        return toAccountInfoResponse(seller);
    }

    private SellerBasicInfoDto.AccountInfoResponse toAccountInfoResponse(Seller seller) {
        LocalDateTime emailChangedAt = seller.getEmailChangedAt();
        LocalDateTime nextChangeableAt = emailChangedAt != null ? emailChangedAt.plusMonths(1) : null;
        boolean changeable = nextChangeableAt == null || !LocalDateTime.now().isBefore(nextChangeableAt);

        return SellerBasicInfoDto.AccountInfoResponse.builder()
                .loginEmail(seller.getEmail())
                .emailChangeable(changeable)
                .lastEmailChangedAt(emailChangedAt)
                .nextEmailChangeableAt(emailChangedAt != null ? nextChangeableAt : null)
                .build();
    }

    private List<SellerBasicInfoDto.ReviewDocumentItem> buildReviewDocuments(Seller seller) {
        List<SellerBasicInfoDto.ReviewDocumentItem> documents = new ArrayList<>();
        addReviewDocumentIfPresent(documents, "BUSINESS_LICENSE", "사업자등록증", seller.getBusinessLicenseImageUrl());
        addReviewDocumentIfPresent(documents, "MAIL_ORDER_REPORT", "통신판매업신고증", seller.getMailOrderRegImageUrl());
        addReviewDocumentIfPresent(documents, "BANKBOOK", "통장사본", seller.getBankbookImageUrl());
        return documents;
    }

    private void addReviewDocumentIfPresent(List<SellerBasicInfoDto.ReviewDocumentItem> documents,
                                             String documentType, String label, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        documents.add(SellerBasicInfoDto.ReviewDocumentItem.builder()
                .documentType(documentType)
                .label(label)
                .fileUrl(fileUrl)
                .extension(extractExtension(fileUrl))
                .build());
    }

    private String extractExtension(String url) {
        int dotIndex = url.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == url.length() - 1) {
            return "";
        }
        String tail = url.substring(dotIndex + 1);
        int queryIndex = tail.indexOf('?');
        return (queryIndex == -1 ? tail : tail.substring(0, queryIndex)).toLowerCase();
    }

    private Seller getMySeller(String sellerEmail) {
        return sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
    }

    private Market getMyMarket(String sellerEmail) {
        Seller seller = getMySeller(sellerEmail);
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}
