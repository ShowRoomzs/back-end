package showroomz.api.seller.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.auth.exception.BusinessException;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.api.seller.market.DTO.MarketDto;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final SellerRepository adminRepository;
    private final CategoryRepository categoryRepository;
    
    // application.yml에서 도메인 주소를 가져오도록 설정
    @Value("${app.base-url:https://showroomz.shop}")
    private String baseUrl;

    /**
     * 마켓 생성 및 URL 자동 할당
     */
    @Transactional
    public void createMarket(Seller admin, String marketName, String csNumber) {
        // 1. 마켓 엔티티 생성
        Market market = new Market(admin, marketName, csNumber);
        
        // 2. 1차 저장 (이 시점에 DB에서 ID가 생성되어 market 객체에 주입됨)
        marketRepository.save(market);

        // 3. 생성된 ID를 기반으로 URL 조합
        String generatedUrl = baseUrl + "/market/" + market.getId();

        // 4. URL 업데이트 (Dirty Checking으로 인해 트랜잭션 종료 시 자동 반영)
        market.setMarketUrl(generatedUrl);
    }

    /**
     * 마켓명 중복 확인
     */
    @Transactional(readOnly = true)
    public MarketDto.CheckMarketNameResponse checkMarketName(String marketName) {
        // REJECTED 상태가 아닌 판매자의 마켓명만 체크 (반려된 계정의 마켓명은 재사용 가능)
        if (marketRepository.existsByMarketNameAndSellerStatusNotRejected(marketName, SellerStatus.REJECTED)) {
            return new MarketDto.CheckMarketNameResponse(false, "DUPLICATE", "이미 사용 중인 마켓명입니다.");
        }
        return new MarketDto.CheckMarketNameResponse(true, "AVAILABLE", "사용 가능한 마켓명입니다.");
    }

    /**
     * 내 마켓 정보 조회
     */
    @Transactional(readOnly = true)
    public MarketDto.MarketProfileResponse getMyMarket(String adminEmail) {
        Market market = getMarketByAdminEmail(adminEmail);

        // Entity의 snsLinks를 List<Dto>로 변환
        List<MarketDto.SnsLinkRequest> snsLinks = market.getSnsLinks().stream()
                .map(sns -> new MarketDto.SnsLinkRequest(sns.getSnsType().name(), sns.getSnsUrl()))
                .collect(java.util.stream.Collectors.toList());

        Category mainCategory = market.getMainCategory();
        return MarketDto.MarketProfileResponse.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .csNumber(market.getCsNumber())
                .marketImageUrl(market.getMarketImageUrl())
                .marketDescription(market.getMarketDescription())
                .marketUrl(market.getMarketUrl())
                .mainCategoryId(mainCategory != null ? mainCategory.getCategoryId() : null)
                .mainCategoryName(mainCategory != null ? mainCategory.getName() : null)
                .snsLinks(snsLinks)
                .followerCount(0L) // 기본값
                .build();
    }

    /**
     * 마켓 프로필 업데이트
     */
    @Transactional
    public void updateMarketProfile(String adminEmail, MarketDto.UpdateMarketProfileRequest request) {
        Market market = getMarketByAdminEmail(adminEmail);

        // 1. 마켓명 변경 및 중복 검증 (이름이 변경된 경우에만)
        if (request.getMarketName() != null && !request.getMarketName().equals(market.getMarketName())) {
            // REJECTED 상태가 아닌 판매자의 마켓명만 체크 (반려된 계정의 마켓명은 재사용 가능)
            if (marketRepository.existsByMarketNameAndSellerStatusNotRejected(request.getMarketName(), SellerStatus.REJECTED)) {
                throw new BusinessException(ErrorCode.DUPLICATE_MARKET_NAME);
            }
            market.setMarketName(request.getMarketName());
        }

        // 2. 마켓 소개 검증 (줄바꿈 제한)
        if (request.getMarketDescription() != null) {
            if (request.getMarketDescription().contains("\n") || request.getMarketDescription().contains("\r")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // 줄바꿈 불가
            }
            market.setMarketDescription(request.getMarketDescription());
        }

        // 3. 기본 필드 업데이트
        if (request.getMarketImageUrl() != null) {
            market.setMarketImageUrl(request.getMarketImageUrl());
        }
        if (request.getMainCategoryId() != null) {
            Category category = categoryRepository.findByCategoryId(request.getMainCategoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
            market.setMainCategory(category);
        }

        // 4. SNS 링크 저장
        market.clearSnsLinks(); // 기존 링크 삭제 (orphanRemoval = true로 인해 DB에서도 삭제됨)

        List<MarketDto.SnsLinkRequest> links = request.getSnsLinks();
        if (links != null && !links.isEmpty()) {
            for (MarketDto.SnsLinkRequest linkDto : links) {
                market.addSnsLink(SnsType.valueOf(linkDto.getSnsType()), linkDto.getSnsUrl());
            }
        }
        
        marketRepository.save(market);
    }

    // --- Helper Methods ---

    private Market getMarketByAdminEmail(String email) {
        // AdminRepository에서 email로 조회
        Seller admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        return marketRepository.findBySeller(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // 마켓 없음
    }


    /**
     * 마켓 이미지 검수 상태 변경 (운영자용)
     */
    // @Transactional
    // public void updateMarketImageStatus(Long marketId, MarketImageStatus status) {
    //     Market market = marketRepository.findById(marketId)
    //             .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
    //     market.setMarketImageStatus(status);
    //     marketRepository.save(market);
    // }
}

