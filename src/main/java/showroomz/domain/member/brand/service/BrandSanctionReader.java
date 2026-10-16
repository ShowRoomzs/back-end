package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.settlement.port.BrandPayoutHoldReader;

import java.util.Optional;

/** 브랜드 정지 읽기 — 정산 포트 {@link BrandPayoutHoldReader}의 구현(52 설계서 6-3) · 파트너 기본정보의 정지 배너 재료. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandSanctionReader implements BrandPayoutHoldReader {

    private final BrandSanctionRepository sanctionRepository;

    @Override
    public boolean isHeld(Long marketId) {
        return marketId != null && sanctionRepository.existsOpenPayoutHold(marketId);
    }

    public Optional<BrandSanction> openSanction(Long marketId) {
        return sanctionRepository.findByMarketIdAndReleasedAtIsNull(marketId);
    }
}
