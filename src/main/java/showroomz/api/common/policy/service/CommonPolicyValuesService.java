package showroomz.api.common.policy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.common.policy.dto.PolicyValuesResponse;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.config.properties.MemberProperties;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.config.properties.SettlementProperties;

@Service
@RequiredArgsConstructor
public class CommonPolicyValuesService {

    private final OrderProperties orderProperties;
    private final DeliveryTrackerProperties trackerProperties;
    private final SettlementProperties settlementProperties;
    private final MemberProperties memberProperties;
    private final GroupBuyProperties groupBuyProperties;

    public PolicyValuesResponse get() {
        return new PolicyValuesResponse(
                orderProperties.getClaim().getAutoReceiveBusinessDays(),
                trackerProperties.getStallAlertDays(),
                trackerProperties.getConsumerChoiceDays(),
                settlementProperties.getReviewBusinessDays(),
                settlementProperties.getPayoutBusinessDays(),
                settlementProperties.getAdjustment().getDeadlineBusinessDays(),
                null, // 고정비 입금 기한 — 설정 없음(정책값 미정)
                memberProperties.getSanction().getAppealBusinessDays(),
                memberProperties.getSanction().getAppealReviewBusinessDays(),
                orderProperties.getCancelRequestRespondBusinessDays(),
                null, // 정산 계좌 확인 — 설정 없음(1021 17-2)
                groupBuyProperties.getSuspension().getAppealBusinessDays());
    }
}
