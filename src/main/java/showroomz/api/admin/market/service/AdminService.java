package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.admin.market.type.RejectionReasonType;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final MailService mailService;

    /**
     * 판매자(관리자) 계정 승인/반려 처리
     */
    @Transactional
    public void updateAdminStatus(Long sellerId, SellerStatus status, 
                                  RejectionReasonType reasonType, String reasonDetail) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        seller.setStatus(status);
        
        if (status == SellerStatus.APPROVED) {
            seller.setRejectionReason(null);
            // 승인 메일 발송
            mailService.sendApprovalEmail(seller.getEmail(), seller.getName());
            
        } else if (status == SellerStatus.REJECTED) {
            // 반려 사유 결정 로직
            String finalReason = resolveRejectionReason(reasonType, reasonDetail);
            seller.setRejectionReason(finalReason);
            // 반려 메일 발송
            mailService.sendRejectionEmail(seller.getEmail(), seller.getName(), finalReason);
        }
        
        seller.setModifiedAt(LocalDateTime.now());
    }

    private String resolveRejectionReason(RejectionReasonType type, String detail) {
        if (type == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // 반려 시 타입 필수
        }
        
        if (type == RejectionReasonType.OTHER) {
            if (detail == null || detail.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // 기타 선택 시 상세 사유 필수
            }
            return detail;
        }
        
        return type.getDescription();
    }

    /**
     * 판매자 상세 정보 조회
     */
    @Transactional(readOnly = true)
    public AdminMarketDto.MarketDetailResponse getMarketDetail(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));

        return AdminMarketDto.MarketDetailResponse.builder()
                .sellerId(seller.getId())
                .marketId(market.getId())
                .email(seller.getEmail())
                .name(seller.getName())
                .marketName(market.getMarketName())
                .phoneNumber(seller.getPhoneNumber())
                .status(seller.getStatus())
                .rejectionReason(seller.getRejectionReason())
                .csNumber(market.getCsNumber())
                .createdAt(seller.getCreatedAt())
                .build();
    }

    /**
     * 마켓 가입 신청 목록 조회 (검색 필터 적용)
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
                        .build())
                .collect(Collectors.toList());

        return new PageResponse<>(content, marketPage);
    }

    /**
     * 마켓 목록 조회 (어드민용)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminMarketDto.MarketResponse> getMarkets(
            AdminMarketDto.MarketListSearchCondition condition, Pageable pageable) {

        Page<AdminMarketDto.MarketResponse> page = marketRepository.findMarketsWithProductCount(
                condition.getMainCategory(),
                condition.getMarketName(),
                SellerStatus.APPROVED,
                pageable
        );

        return new PageResponse<>(page.getContent(), page);
    }

    /**
     * 마켓 정보 관리용 상세 조회
     */
    @Transactional(readOnly = true)
    public AdminMarketDto.MarketAdminDetailResponse getMarketInfo(Long marketId) {
        // SellerId가 아니라 MarketId로 조회하는 경우가 많음 (어드민 마켓 목록에서 클릭해서 들어오므로)
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        
        return AdminMarketDto.MarketAdminDetailResponse.from(market);
    }
}

