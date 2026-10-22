package showroomz.domain.member.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

/**
 * {@link MemberNotifier}의 알림함 구현(1017 설계서 1-4 · 1-5) — 브랜드 · 인플루언서 조치 결과를 파트너센터 · 쇼룸 스튜디오 알림함에 쓴다.
 * 메일 · 문자 외부 발송은 결정 대기(12절 Q1)라 하지 않는다(변경 요청 승인 메일은 기존 경로가 따로 보낸다).
 *
 * <p>정지 · 해제 알림은 운영팀 채널의 정지 카드로 보낸다(파트너 알림 표 「연결·소통 › 운영팀 채널 · 정지 카드」). 호출은 커밋 뒤다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxMemberNotifier implements MemberNotifier {

    private final NotificationPublisher publisher;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;
    private final BrandSanctionRepository brandSanctions;
    private final CreatorSanctionRepository creatorSanctions;
    private final OperatorChannelService operatorChannels;

    @Override
    @Transactional(readOnly = true)
    public void suspended(MemberType type, Long memberId, String reasonLabel) {
        Channel channel = channel(type, memberId, true);
        publish(type, memberId, NotificationType.ACCOUNT_SUSPENDED,
                type == MemberType.BRAND ? "계정 이용이 정지되었습니다 · 사유: " + reasonLabel + " — 운영팀 채널에서 소명할 수 있습니다"
                        : "계정 이용이 정지됐어요 · 사유: " + reasonLabel + " · 운영팀 채널에서 소명할 수 있어요",
                NotificationTargetType.THREAD, channel.threadId(), channel.cardId());
    }

    @Override
    @Transactional(readOnly = true)
    public void released(MemberType type, Long memberId) {
        Channel channel = channel(type, memberId, false);
        publish(type, memberId, NotificationType.ACCOUNT_RELEASED,
                type == MemberType.BRAND ? "계정 이용 정지가 해제되었습니다" : "계정 이용 정지가 해제됐어요",
                NotificationTargetType.THREAD, channel.threadId(), channel.cardId());
    }

    @Override
    public void withdrawalApproved(MemberType type, Long memberId, String recipient) {
        // 탈퇴 승인으로 계정이 해지됐다 — 알림함을 볼 수 없다. 메일 경로만 남는다(외부 발송 결정 대기).
        log.info("[알림] {} 탈퇴 승인 - id: {}, hasRecipient: {}", type.getLabel(), memberId, recipient != null);
    }

    @Override
    public void withdrawalRejected(MemberType type, Long memberId, String reasonLabel) {
        publish(type, memberId, NotificationType.WITHDRAWAL_REJECTED,
                type == MemberType.BRAND ? "탈퇴 신청이 반려되었습니다 · 사유: " + reasonLabel
                        : "탈퇴 신청이 반려됐어요 · 사유: " + reasonLabel,
                NotificationTargetType.BASIC_INFO_TAB, null, type == MemberType.BRAND ? "ACCOUNT" : "ACCOUNT");
    }

    @Override
    public void accountVerificationFailed(Long creatorId, String reasonLabel) {
        publish(MemberType.CREATOR, creatorId, NotificationType.ACCOUNT_VERIFICATION_FAILED,
                "정산 계좌 실명 확인에 실패했어요 · " + reasonLabel, NotificationTargetType.BASIC_INFO_TAB, null,
                "SETTLEMENT");
    }

    @Override
    public void businessTypeChangeApproved(Long creatorId, String businessEmail, String requestCode) {
        publish(MemberType.CREATOR, creatorId, NotificationType.BUSINESS_TYPE_CHANGE_APPROVED,
                "사업자 구분 변경 요청이 승인됐어요(" + requestCode + ")", NotificationTargetType.BASIC_INFO_TAB, null,
                "SETTLEMENT");
    }

    @Override
    public void businessTypeChangeRejected(Long creatorId, String businessEmail, String requestCode, String reasonLabel) {
        publish(MemberType.CREATOR, creatorId, NotificationType.BUSINESS_TYPE_CHANGE_REJECTED,
                "사업자 구분 변경 요청이 반려됐어요 · 사유: " + reasonLabel, NotificationTargetType.BASIC_INFO_TAB, null,
                "SETTLEMENT");
    }

    @Override
    public void accountChangeApproved(Long creatorId, String requestCode) {
        publish(MemberType.CREATOR, creatorId, NotificationType.ACCOUNT_CHANGE_APPROVED,
                "정산 계좌 변경 요청이 승인됐어요(" + requestCode + ") · 새 계좌를 확인하고 있어요", NotificationTargetType.BASIC_INFO_TAB,
                null, "SETTLEMENT");
    }

    @Override
    public void accountChangeRejected(Long creatorId, String requestCode, String reasonLabel) {
        publish(MemberType.CREATOR, creatorId, NotificationType.ACCOUNT_CHANGE_REJECTED,
                "정산 계좌 변경 요청이 반려됐어요 · 사유: " + reasonLabel + " · 기존 계좌로 지급돼요",
                NotificationTargetType.BASIC_INFO_TAB, null, "SETTLEMENT");
    }

    // ------------------------------------------------------------------

    private record Channel(Long threadId, String cardId) {
    }

    private Channel channel(MemberType type, Long memberId, boolean suspendedCard) {
        try {
            if (type == MemberType.BRAND) {
                Long thread = marketRepository.findById(memberId)
                        .flatMap(operatorChannels::findMarketChannelThreadId).orElse(null);
                Long card = brandSanctions.findFirstByMarketIdOrderBySuspendedAtDesc(memberId)
                        .map(s -> suspendedCard ? s.getSuspendCardId() : s.getReleaseCardId()).orElse(null);
                return new Channel(thread, card == null ? null : String.valueOf(card));
            }
            if (type == MemberType.CREATOR) {
                Long thread = creatorRepository.findById(memberId)
                        .flatMap(operatorChannels::findCreatorChannelThreadId).orElse(null);
                Long card = creatorSanctions.findFirstByCreatorIdOrderBySuspendedAtDesc(memberId)
                        .map(s -> suspendedCard ? s.getSuspendCardId() : s.getReleaseCardId()).orElse(null);
                return new Channel(thread, card == null ? null : String.valueOf(card));
            }
        } catch (RuntimeException e) {
            log.warn("정지 알림 채널 조회 실패 - {} {}", type, memberId, e);
        }
        return new Channel(null, null);
    }

    private void publish(MemberType type, Long memberId, NotificationType notificationType, String title,
                         NotificationTargetType targetType, Long targetId, String targetSub) {
        NotificationCommand.NotificationCommandBuilder builder = switch (type) {
            case BRAND -> NotificationCommand.toSeller(memberId, notificationType);
            case CREATOR -> NotificationCommand.toCreator(memberId, notificationType);
            default -> null;
        };
        if (builder == null) {
            return;
        }
        publisher.publish(builder.actorType(NotificationActorType.OPERATOR).title(title)
                .targetType(targetType).targetId(targetId).targetSub(targetSub).build());
    }
}
