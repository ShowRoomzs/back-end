package showroomz.api.admin.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;

    /**
     * 판매자(관리자) 계정 승인/반려 처리
     */
    @Transactional
    public void updateAdminStatus(Long adminId, SellerStatus status) {
        Seller admin = sellerRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        admin.setStatus(status);
        admin.setModifiedAt(LocalDateTime.now());
    }

    /**
     * 가입 대기 판매자 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<SellerDto.PendingSellerResponse> getPendingSellers(Pageable pageable) {
        // PENDING 상태인 Admin을 가진 Market 목록 조회 (페이징)
        Page<Market> pendingMarkets = marketRepository.findAllByAdmin_Status(SellerStatus.PENDING, pageable);

        // Market -> DTO 변환
        List<SellerDto.PendingSellerResponse> content = pendingMarkets.getContent().stream()
                .map(market -> SellerDto.PendingSellerResponse.builder()
                        .adminId(market.getSeller().getId())
                        .email(market.getSeller().getEmail())
                        .name(market.getSeller().getName())
                        .marketName(market.getMarketName())
                        .phoneNumber(market.getSeller().getPhoneNumber())
                        .createdAt(market.getSeller().getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResponse<>(content, pendingMarkets);
    }
}

