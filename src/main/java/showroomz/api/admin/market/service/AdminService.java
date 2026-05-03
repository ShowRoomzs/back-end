package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.type.RejectionReasonType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    /** 정산·판매 실적 연동 전까지 관리자 상세용 더미 값 */
    private static final long DUMMY_TOTAL_SALES_AMOUNT = 12_450_000L;
    private static final long DUMMY_MONTHLY_SALES_AMOUNT = 1_230_000L;
    private static final long DUMMY_TOTAL_ORDER_COUNT = 842L;
    private static final long DUMMY_MONTHLY_ORDER_COUNT = 67L;
    private static final LocalDate DUMMY_LAST_SETTLEMENT_DATE = LocalDate.of(2026, 4, 25);
    private static final long DUMMY_UNSETTLED_AMOUNT = 340_000L;

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final ProductRepository productRepository;
    private final MailService mailService;

    /**
     * 마켓(SELLER) 계정 승인/반려 처리
     */
    @Transactional
    public void updateAdminStatus(Long sellerId, SellerStatus status, 
                                  RejectionReasonType reasonType, String reasonDetail) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (seller.getRoleType() != RoleType.SELLER) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_PENDING);
        }

        validateRejectionReasonTypeWhenRejected(status, reasonType);

        LocalDateTime processedAt = LocalDateTime.now();
        String marketName = marketRepository.findBySeller(seller)
                .map(Market::getMarketName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(seller.getName());

        applySellerStatusAndRejectionFields(seller, status, reasonType, reasonDetail);

        if (status == SellerStatus.APPROVED) {
            mailService.sendApprovalEmail(seller.getEmail(), marketName, processedAt);

        } else if (status == SellerStatus.REJECTED) {
            String mailDetail = reasonDetail != null && !reasonDetail.isBlank() ? reasonDetail.strip() : "";
            mailService.sendRejectionEmail(
                    seller.getEmail(), marketName, processedAt, reasonType.getDescription(), mailDetail);
        }

        seller.setProcessedAt(processedAt);
        seller.setModifiedAt(processedAt);
    }

    /**
     * 크리에이터(CREATOR) 계정 승인/반려 처리
     */
    @Transactional
    public void updateCreatorStatus(Long sellerId, SellerStatus status,
                                    RejectionReasonType reasonType, String reasonDetail) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (seller.getRoleType() != RoleType.CREATOR) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_PENDING);
        }

        validateRejectionReasonTypeWhenRejected(status, reasonType);

        applySellerStatusAndRejectionFields(seller, status, reasonType, reasonDetail);

        if (status == SellerStatus.APPROVED) {
            mailService.sendCreatorApprovalEmail(seller.getEmail(), seller.getName());

        } else if (status == SellerStatus.REJECTED) {
            mailService.sendCreatorRejectionEmail(
                    seller.getEmail(), seller.getName(), buildCreatorRejectionMailReason(reasonType, reasonDetail));
        }

        seller.setProcessedAt(LocalDateTime.now());
        seller.setModifiedAt(LocalDateTime.now());
    }

    /**
     * 셀러 검토 메모 수정
     */
    @Transactional
    public void updateReviewMemo(Long sellerId, String reviewMemo) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (seller.getRoleType() != RoleType.SELLER) {
            throw new BusinessException(ErrorCode.ACCOUNT_ROLE_MISMATCH);
        }

        seller.setReviewMemo(reviewMemo);
        seller.setModifiedAt(LocalDateTime.now());
    }

    private void applySellerStatusAndRejectionFields(Seller seller, SellerStatus newStatus,
                                                     RejectionReasonType reasonType, String reasonDetail) {
        seller.setStatus(newStatus);
        seller.setRejectionReasonDetail(reasonDetail);

        if (newStatus == SellerStatus.REJECTED) {
            seller.setRejectionReason(reasonType.name());
        } else {
            seller.setRejectionReason(null);
        }
    }

    private void validateRejectionReasonTypeWhenRejected(SellerStatus status, RejectionReasonType reasonType) {
        if (status == SellerStatus.REJECTED && reasonType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String buildCreatorRejectionMailReason(RejectionReasonType type, String detail) {
        String detailText = (detail == null || detail.isBlank()) ? "" : detail.strip();
        boolean hasDetail = !detailText.isEmpty();

        if (type == RejectionReasonType.OTHER) {
            return hasDetail ? detailText : type.getDescription();
        }
        if (hasDetail) {
            return type.getDescription() + " - " + detailText;
        }
        return type.getDescription();
    }

    /**
     * 마켓(SELLER) 가입 신청 목록 조회 (검색 필터 적용)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminMarketDto.ApplicationResponse> getMarketApplications(
            AdminMarketDto.SearchCondition condition, Pageable pageable) {

        LocalDateTime startDateTime = condition.getStartDate() != null 
                ? condition.getStartDate().atStartOfDay() 
                : null;
        LocalDateTime endDateTime = condition.getEndDate() != null 
                ? condition.getEndDate().atTime(LocalTime.MAX) 
                : null;

        // Enum 타입을 String으로 변환 (null 체크 포함)
        String keywordTypeStr = condition.getKeywordType() != null 
                ? condition.getKeywordType().name() 
                : null;

        Page<Market> marketPage = marketRepository.searchApplications(
                RoleType.SELLER,
                condition.getStatus(),
                startDateTime,
                endDateTime,
                condition.getKeyword(),
                keywordTypeStr,
                pageable
        );

        List<AdminMarketDto.ApplicationResponse> content = marketPage.getContent().stream()
                .map(market -> AdminMarketDto.ApplicationResponse.builder()
                        .sellerId(market.getSeller().getId())
                        .marketId(market.getId())
                        .email(market.getSeller().getEmail())
                        .name(market.getSeller().getName())
                        .marketName(market.getMarketName())
                        .phoneNumber(market.getSeller().getPhoneNumber())
                        .status(market.getSeller().getStatus())
                        .rejectionReason(market.getSeller().getRejectionReason())
                        .createdAt(market.getSeller().getCreatedAt())
                        .businessType(market.getSeller().getBusinessType())
                        .businessNumber(market.getSeller().getBusinessRegistrationNumber())
                        .processedAt(market.getSeller().getProcessedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResponse<>(content, marketPage);
    }

    /**
     * 크리에이터(CREATOR) 가입 신청 목록 조회 (검색 필터 적용)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminMarketDto.CreatorApplicationResponse> getCreatorApplications(
            AdminMarketDto.CreatorSearchCondition condition, Pageable pageable) {

        LocalDateTime startDateTime = condition.getStartDate() != null
                ? condition.getStartDate().atStartOfDay()
                : null;
        LocalDateTime endDateTime = condition.getEndDate() != null
                ? condition.getEndDate().atTime(LocalTime.MAX)
                : null;

        String keywordTypeStr = condition.getKeywordType() != null
                ? condition.getKeywordType().toQueryType()
                : null;

        Page<Market> marketPage = marketRepository.searchApplications(
                RoleType.CREATOR,
                condition.getStatus(),
                startDateTime,
                endDateTime,
                condition.getKeyword(),
                keywordTypeStr,
                pageable
        );

        List<AdminMarketDto.CreatorApplicationResponse> content = marketPage.getContent().stream()
                .map(market -> AdminMarketDto.CreatorApplicationResponse.builder()
                        .creatorId(market.getSeller().getId())
                        .showroomName(market.getMarketName())
                        .createdAt(market.getSeller().getCreatedAt())
                        .name(market.getSeller().getName())
                        .phoneNumber(market.getSeller().getPhoneNumber())
                        .status(market.getSeller().getStatus())
                        .rejectionReason(market.getSeller().getRejectionReason())
                        .build())
                .collect(Collectors.toList());

        return new PageResponse<>(content, marketPage);
    }

    /**
     * 크리에이터 상세 정보 조회
     */
    @Transactional(readOnly = true)
    public AdminMarketDto.CreatorDetailResponse getCreatorDetail(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        String platformUrl = market.getSnsLinks().isEmpty()
                ? null
                : market.getSnsLinks().get(0).getSnsUrl();
        SnsType platformType = market.getSnsLinks().isEmpty()
                ? null
                : market.getSnsLinks().get(0).getSnsType();

        return AdminMarketDto.CreatorDetailResponse.builder()
                .creatorId(seller.getId())
                .email(seller.getEmail())
                .showroomName(market.getMarketName())
                .activityName(seller.getActivityName())
                .platformType(platformType)
                .platformUrl(platformUrl)
                .name(seller.getName())
                .phoneNumber(seller.getPhoneNumber())
                .status(seller.getStatus())
                .rejectionReason(seller.getRejectionReason())
                .build();
    }

    /**
     * 마켓 정보 관리용 상세 조회
     */
    @Transactional(readOnly = true)
    public AdminMarketDto.MarketAdminDetailResponse getMarketInfo(Long marketId) {
        Market market = marketRepository.findByIdWithSeller(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        long registeredCount = productRepository.countByMarket_Id(marketId);
        long pendingInspectionCount = productRepository.countByMarket_IdAndInspectionStatus(
                marketId, ProductInspectionStatus.WAITING);

        Seller seller = market.getSeller();
        LocalDateTime processedAt = seller.getProcessedAt();
        int operatingMonths = computeOperatingMonths(processedAt);

        List<AdminMarketDto.SnsLinkResponse> snsLinks = market.getSnsLinks().stream()
                .map(sns -> new AdminMarketDto.SnsLinkResponse(sns.getSnsType().name(), sns.getSnsUrl()))
                .collect(Collectors.toList());

        return AdminMarketDto.MarketAdminDetailResponse.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .csNumber(market.getCsNumber())
                .marketImageUrl(market.getMarketImageUrl())
                .marketDescription(market.getMarketDescription())
                .marketUrl(market.getMarketUrl())
                .mainCategoryId(market.getMainCategory() != null ? market.getMainCategory().getCategoryId() : null)
                .mainCategoryName(market.getMainCategory() != null ? market.getMainCategory().getName() : null)
                .snsLinks(snsLinks)
                .registeredProductCount(registeredCount)
                .pendingInspectionProductCount(pendingInspectionCount)
                .totalSalesAmount(DUMMY_TOTAL_SALES_AMOUNT)
                .monthlySalesAmount(DUMMY_MONTHLY_SALES_AMOUNT)
                .totalOrderCount(DUMMY_TOTAL_ORDER_COUNT)
                .monthlyOrderCount(DUMMY_MONTHLY_ORDER_COUNT)
                .processedDate(processedAt)
                .operatingMonths(operatingMonths)
                .marketStatus(market.getStatus())
                .adminMemo(market.getAdminMemo())
                .joinedAt(seller.getCreatedAt())
                .lastSettlementDate(DUMMY_LAST_SETTLEMENT_DATE)
                .unsettledAmount(DUMMY_UNSETTLED_AMOUNT)
                .lastLoginAt(seller.getLastLoginAt())
                .businessType(seller.getBusinessType())
                .representativeName(seller.getRepresentativeName())
                .representativeContact(seller.getRepresentativeContact())
                .companyName(seller.getCompanyName())
                .businessRegistrationNumber(seller.getBusinessRegistrationNumber())
                .businessCondition(seller.getBusinessCondition())
                .businessAddress(seller.getBusinessAddress())
                .detailAddress(seller.getDetailAddress())
                .taxEmail(seller.getTaxEmail())
                .businessLicenseImageUrl(seller.getBusinessLicenseImageUrl())
                .mailOrderRegImageUrl(seller.getMailOrderRegImageUrl())
                .mailOrderRegNumber(seller.getMailOrderRegNumber())
                .bankName(seller.getBankName())
                .accountHolder(seller.getAccountHolder())
                .accountNumber(seller.getAccountNumber())
                .bankbookImageUrl(seller.getBankbookImageUrl())
                .build();
    }

    private static int computeOperatingMonths(LocalDateTime processedAt) {
        if (processedAt == null) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(processedAt.toLocalDate(), LocalDate.now());
        return (int) Math.max(0, months);
    }
}

