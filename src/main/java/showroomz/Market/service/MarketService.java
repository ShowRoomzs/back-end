package showroomz.Market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.Market.DTO.MarketDto;
import showroomz.Market.entity.Market;
import showroomz.Market.type.MarketImageStatus;
import showroomz.admin.entity.Admin;
import showroomz.admin.repository.AdminRepository;
import showroomz.auth.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.Market.repository.MarketRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final AdminRepository adminRepository;
    
    // application.yml에서 도메인 주소를 가져오도록 설정
    @Value("${app.base-url:https://showroomz.shop}")
    private String baseUrl;

    /**
     * 마켓 생성 및 URL 자동 할당
     */
    @Transactional
    public void createMarket(Admin admin, String marketName, String csNumber) {
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
        if (marketRepository.existsByMarketName(marketName)) {
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

        // Entity의 snsLink1, 2, 3을 List<Dto>로 변환
        List<MarketDto.SnsLinkRequest> snsLinks = new ArrayList<>();
        parseAndAddSnsLink(market.getSnsLink1(), snsLinks);
        parseAndAddSnsLink(market.getSnsLink2(), snsLinks);
        parseAndAddSnsLink(market.getSnsLink3(), snsLinks);

        return MarketDto.MarketProfileResponse.builder()
                .marketId(market.getId())
                .marketName(market.getMarketName())
                .csNumber(market.getCsNumber())
                .marketImageUrl(market.getMarketImageUrl())
                .marketImageStatus(market.getMarketImageStatus() != null ? market.getMarketImageStatus().name() : MarketImageStatus.APPROVED.name())
                .marketDescription(market.getMarketDescription())
                .marketUrl(market.getMarketUrl())
                .mainCategory(market.getMainCategory())
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
            if (marketRepository.existsByMarketName(request.getMarketName())) {
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
        // 마켓 대표 이미지가 변경된 경우 검수 상태를 '검수 중'으로 변경
        if (request.getMarketImageUrl() != null) {
            // 기존 이미지와 다른 새로운 URL이 들어오면
            if (!request.getMarketImageUrl().equals(market.getMarketImageUrl())) {
                market.setMarketImageUrl(request.getMarketImageUrl());
                market.setMarketImageStatus(MarketImageStatus.UNDER_REVIEW); // 검수 중으로 변경
            }
        }
        if (request.getMainCategory() != null) market.setMainCategory(request.getMainCategory());

        // 4. SNS 링크 저장 (List -> Entity 필드 1,2,3 매핑)
        // 기존 링크 초기화
        market.setSnsLink1(null);
        market.setSnsLink2(null);
        market.setSnsLink3(null);

        List<MarketDto.SnsLinkRequest> links = request.getSnsLinks();
        if (links != null && !links.isEmpty()) {
            // DB 저장을 위해 "TYPE|URL" 형식으로 조합하여 저장
            if (links.size() >= 1) market.setSnsLink1(combineSnsInfo(links.get(0)));
            if (links.size() >= 2) market.setSnsLink2(combineSnsInfo(links.get(1)));
            if (links.size() >= 3) market.setSnsLink3(combineSnsInfo(links.get(2)));
        }
        
        marketRepository.save(market);
    }

    // --- Helper Methods ---

    private Market getMarketByAdminEmail(String email) {
        // AdminRepository에서 email로 조회
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        return marketRepository.findByAdmin(admin)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // 마켓 없음
    }

    // DB의 문자열을 DTO로 파싱 ("TYPE|URL" 구조 가정)
    private void parseAndAddSnsLink(String linkString, List<MarketDto.SnsLinkRequest> list) {
        if (linkString != null && !linkString.isEmpty()) {
            String[] parts = linkString.split("\\|", 2);
            if (parts.length == 2) {
                list.add(new MarketDto.SnsLinkRequest(parts[0], parts[1]));
            } else {
                // 형식이 맞지 않으면 URL만이라도 넣거나 무시
                list.add(new MarketDto.SnsLinkRequest("UNKNOWN", linkString));
            }
        }
    }

    // DTO를 DB 저장용 문자열로 변환
    private String combineSnsInfo(MarketDto.SnsLinkRequest dto) {
        if (dto.getSnsType() == null || dto.getSnsUrl() == null) return null;
        return dto.getSnsType() + "|" + dto.getSnsUrl();
    }

    /**
     * 마켓 이미지 검수 상태 변경 (운영자용)
     */
    @Transactional
    public void updateMarketImageStatus(Long marketId, MarketImageStatus status) {
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        market.setMarketImageStatus(status);
        marketRepository.save(market);
    }
}

