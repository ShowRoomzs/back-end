package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.admin.market.DTO.AdminMarketDto;
import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.DTO.SellerDto;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.dto.PageResponse;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;

    /**
     * 판매자(관리자) 계정 승인/반려 처리
     * PENDING 상태일 때만 변경 가능
     */
    @Transactional
    public void updateAdminStatus(Long adminId, SellerStatus status, String rejectionReason) {
        Seller admin = sellerRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // PENDING 상태일 때만 변경 가능
        if (admin.getStatus() != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        admin.setStatus(status);
        
        // REJECTED 상태일 때만 rejectionReason 저장
        if (status == SellerStatus.REJECTED) {
            admin.setRejectionReason(rejectionReason);
        } else {
            // APPROVED 상태일 때는 rejectionReason 초기화
            admin.setRejectionReason(null);
        }
        
        admin.setModifiedAt(LocalDateTime.now());
    }

    /**
     * 가입 대기 판매자 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<SellerDto.PendingSellerResponse> getPendingSellers(Pageable pageable) {
        // PENDING 상태인 Seller를 가진 Market 목록 조회 (페이징)
        Page<Market> pendingMarkets = marketRepository.findAllBySeller_Status(SellerStatus.PENDING, pageable);

        // Market -> DTO 변환
        List<SellerDto.PendingSellerResponse> content = pendingMarkets.getContent().stream()
                .map(market -> SellerDto.PendingSellerResponse.builder()
                        .sellerId(market.getSeller().getId())
                        .email(market.getSeller().getEmail())
                        .name(market.getSeller().getName())
                        .marketName(market.getMarketName())
                        .phoneNumber(market.getSeller().getPhoneNumber())
                        .createdAt(market.getSeller().getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResponse<>(content, pendingMarkets);
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

        Page<Market> marketPage = marketRepository.searchApplications(
                condition.getStatus(),
                startDateTime,
                endDateTime,
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
}

