package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;

import java.util.Optional;

/** 브랜드 정지 읽기 — 파트너 기본정보의 정지 배너 재료(52 설계서 7절). 지급 정지 판정은 정산 포트 {@code PayoutHoldReader}가 한다. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandSanctionReader {

    private final BrandSanctionRepository sanctionRepository;
    private final BrandWithdrawalRequestRepository withdrawalRequests;

    public Optional<BrandSanction> openSanction(Long marketId) {
        return sanctionRepository.findByMarketIdAndReleasedAtIsNull(marketId);
    }

    /** 탈퇴 신청 심사 중인가 — 열린 신청 행이 있으면(51 설계서 1-4). 취소 · 반려되면 행이 대기가 아니게 되어 잠금이 저절로 풀린다. */
    public boolean hasPendingWithdrawal(Long marketId) {
        return marketId != null && withdrawalRequests.existsByMarketIdAndStatus(marketId, MemberWithdrawalStatus.PENDING);
    }
}
