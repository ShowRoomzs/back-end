package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.common.type.MemberWithdrawalStatus;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;

import java.util.Optional;

/**
 * 인플루언서 정지 읽기 — 스튜디오가 막힌 기능을 「비활성 + 안내」로 그릴 재료(53 설계서 4-2 · 12-1). 파트너 기본정보의
 * {@code BrandSanctionReader}와 짝이다. 지급 정지 판정은 정산 포트 {@code PayoutHoldReader}가 한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorSanctionReader {

    private final CreatorSanctionRepository sanctions;
    private final CreatorWithdrawalRequestRepository withdrawalRequests;

    public Optional<CreatorSanction> openSanction(Long creatorId) {
        return sanctions.findByCreatorIdAndReleasedAtIsNull(creatorId);
    }

    /** 탈퇴 신청 심사 중인가 — 새 연결 수락 · 계약 서명이 막힌다(스튜디오 S7). */
    public boolean hasPendingWithdrawal(Long creatorId) {
        return withdrawalRequests.existsByCreatorIdAndStatus(creatorId, MemberWithdrawalStatus.PENDING);
    }
}
