package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.brand.entity.BrandAdminHistory;
import showroomz.domain.member.brand.repository.BrandAdminHistoryRepository;
import showroomz.domain.member.brand.type.BrandAdminActorType;
import showroomz.domain.member.brand.type.BrandAdminEventType;

import java.time.LocalDateTime;

/** 브랜드 처리 이력 쓰기(52 설계서 0-6) — 저장 대상 9종만 받는다. 조치와 같은 트랜잭션이어야 한다. */
@Component
@RequiredArgsConstructor
public class BrandAdminHistoryRecorder {

    public static final String REF_SANCTION = "BRAND_SANCTION";
    public static final String REF_WITHDRAWAL = "BRAND_WITHDRAWAL_REQUEST";
    public static final String REF_MEMO = "BRAND_ADMIN_MEMO";

    private final BrandAdminHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void byAdmin(Long marketId, BrandAdminEventType type, Long operatorId, String operatorName, String detail,
                        String refType, Long refId, LocalDateTime now) {
        record(marketId, type, BrandAdminActorType.ADMIN, operatorId, operatorName, detail, refType, refId, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void byBrand(Long marketId, BrandAdminEventType type, String requesterName, String detail, String refType,
                        Long refId, LocalDateTime now) {
        record(marketId, type, BrandAdminActorType.BRAND, null, requesterName, detail, refType, refId, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bySystem(Long marketId, BrandAdminEventType type, String detail, String refType, Long refId,
                         LocalDateTime now) {
        record(marketId, type, BrandAdminActorType.SYSTEM, null, null, detail, refType, refId, now);
    }

    private void record(Long marketId, BrandAdminEventType type, BrandAdminActorType actorType, Long actorId,
                        String actorName, String detail, String refType, Long refId, LocalDateTime now) {
        if (!type.isStored()) {
            throw new IllegalArgumentException("파생 이력은 저장하지 않는다: " + type);
        }
        historyRepository.save(BrandAdminHistory.of(marketId, type, actorType, actorId, actorName, detail, refType,
                refId, now));
    }
}
