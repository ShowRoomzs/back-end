package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;

import java.util.Optional;

/** 브랜드 정지 읽기 — 파트너 기본정보의 정지 배너 재료(52 설계서 7절). 지급 정지 판정은 정산 포트 {@code PayoutHoldReader}가 한다. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandSanctionReader {

    private final BrandSanctionRepository sanctionRepository;

    public Optional<BrandSanction> openSanction(Long marketId) {
        return sanctionRepository.findByMarketIdAndReleasedAtIsNull(marketId);
    }
}
