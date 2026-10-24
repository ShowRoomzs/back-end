package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

/**
 * 계약 상태 전이 알림 — 파트너센터 · 쇼룸 스튜디오 알림함으로 보낸다(1017 설계서 1-4 · 1-5). 어드민 통지는 여전히 로그다(어드민
 * 알림함은 범위 밖 — 어드민 홈의 할 일 큐가 그 역할을 한다).
 *
 * <p>호출 지점의 이벤트 문자열을 알림 유형 · 문장으로 옮긴다. 모르는 이벤트는 조용히 넘긴다(알림 대상이 아닌 전이).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractNotifier {

    private final NotificationPublisher publisher;

    public void notifyBothParties(Contract contract, String event) {
        notifySeller(contract, event);
        notifyCounterparty(contract, event);
    }

    public void notifySeller(Contract contract, String event) {
        NotificationType type;
        NotificationActorType actor = NotificationActorType.OPERATOR;
        String actorName = null;
        String title = titleOf(contract);
        String sentence;
        switch (event) {
            case "REVIEW_APPROVED" -> {
                type = NotificationType.CONTRACT_REVIEW_PASSED;
                sentence = "계약 「" + title + "」 검토를 통과했습니다 — 메일 · 문자로 서명 안내가 발송됩니다";
            }
            case "REVIEW_REJECTED" -> {
                type = NotificationType.CONTRACT_REVIEW_REJECTED;
                sentence = "계약 「" + title + "」가 반려되었습니다"
                        + (contract.getRejectReasonDetail() == null ? "" : " — " + contract.getRejectReasonDetail());
            }
            case "CONCLUDED" -> {
                type = NotificationType.CONTRACT_CONCLUDED;
                sentence = "계약 「" + title + "」 공구가 생성되었습니다 — 인플루언서 게시물 등록 · 운영팀 오픈 승인 뒤 오픈됩니다";
            }
            case "ESTABLISHED" -> {
                type = NotificationType.CONTRACT_ESTABLISHED;
                sentence = "계약 「" + title + "」 양측 서명이 완료되어 계약이 성립했습니다 — 운영팀이 공구를 생성합니다";
            }
            case "TERMINATED" -> {
                type = NotificationType.CONTRACT_TERMINATED;
                sentence = "계약 「" + title + "」가 해지되었습니다 — 사유는 운영팀 채널에서 확인할 수 있습니다";
            }
            case "EXPIRED" -> {
                type = NotificationType.CONTRACT_EXPIRED;
                sentence = "계약 「" + title + "」가 서명 기한이 지나 만료되었습니다";
            }
            case "CANCELED" -> {
                type = NotificationType.CONTRACT_CANCELED_BY_ADMIN;
                sentence = "계약 「" + title + "」가 운영팀에 의해 취소되었습니다";
            }
            case "RESEND_HANDLED" -> {
                type = NotificationType.CONTRACT_RESEND_HANDLED;
                sentence = "계약 「" + title + "」 서명 안내가 다시 발송되었습니다";
            }
            case "DECLINED" -> {
                type = NotificationType.CONTRACT_DECLINED;
                actor = NotificationActorType.CREATOR;
                actorName = creatorName(contract);
                sentence = "「" + title + "」 계약서 서명을 거절했습니다";
            }
            case "CREATOR_SIGNED" -> {
                type = NotificationType.CONTRACT_COUNTERPARTY_SIGNED;
                actor = NotificationActorType.CREATOR;
                actorName = creatorName(contract);
                sentence = "「" + title + "」 계약서에 서명했습니다";
            }
            default -> {
                return;
            }
        }
        publisher.publish(NotificationCommand.toSeller(contract.getMarket().getId(), type)
                .actorType(actor).actorName(actorName).title(sentence)
                .targetType(NotificationTargetType.CONTRACT).targetId(contract.getId())
                .targetSub(type == NotificationType.CONTRACT_COUNTERPARTY_SIGNED ? "SIGNATURE" : null)
                .build());
    }

    /** 검토 요청 · 요청 취소 → 어드민(어드민 알림함은 없다 — 홈 할 일 큐가 맡는다). */
    public void notifyAdmin(Contract contract, String event) {
        log.info("[contract-notify] to=ADMIN event={} contractId={} number={}",
                event, contract.getId(), contract.getContractNumber());
    }

    /** 상대(인플루언서) — 서명 요청 도착 · 브랜드 서명 · 체결 · 만료 · 취소 · 고정 지급비 지급 기록. */
    public void notifyCounterparty(Contract contract, String event) {
        if (contract.getCreator() == null) {
            return;
        }
        String brand = contract.getMarket().getMarketName();
        String title = titleOf(contract);
        NotificationType type;
        NotificationActorType actor = NotificationActorType.OPERATOR;
        String actorName = null;
        String sentence;
        switch (event) {
            case "REVIEW_APPROVED" -> {
                type = NotificationType.CONTRACT_SIGNATURE_REQUESTED;
                actor = NotificationActorType.BRAND;
                actorName = brand;
                sentence = brand + "가 「" + title + "」 계약서를 보냈어요 · 메일 · 문자로 받은 링크에서 서명해 주세요";
            }
            case "BRAND_SIGNED" -> {
                type = NotificationType.CONTRACT_COUNTERPARTY_SIGNED;
                actor = NotificationActorType.BRAND;
                actorName = brand;
                sentence = brand + "가 「" + title + "」 계약에 서명했어요 · 이제 내 서명만 남았어요";
            }
            case "CONCLUDED" -> {
                type = NotificationType.CONTRACT_CONCLUDED;
                sentence = "「" + title + "」 공구가 생성됐어요 · 공구 게시물을 작성할 수 있어요";
            }
            case "ESTABLISHED" -> {
                type = NotificationType.CONTRACT_ESTABLISHED;
                sentence = "「" + title + "」 양측 서명이 끝나 계약이 성립했어요 · 운영팀이 공구를 생성해요";
            }
            case "TERMINATION_REQUESTED" -> {
                type = NotificationType.CONTRACT_TERMINATION_REQUESTED;
                actor = NotificationActorType.BRAND;
                actorName = brand;
                sentence = brand + "가 「" + title + "」 계약 해지를 요청했어요 · 의견은 운영팀 채널에 남겨 주세요";
            }
            case "TERMINATED" -> {
                type = NotificationType.CONTRACT_TERMINATED;
                sentence = "「" + title + "」 계약이 해지됐어요 · 사유는 운영팀 채널에서 확인할 수 있어요";
            }
            case "EXPIRED" -> {
                type = NotificationType.CONTRACT_EXPIRED;
                sentence = "「" + title + "」 계약이 서명 기한이 지나 만료됐어요";
            }
            case "CANCELED" -> {
                type = NotificationType.CONTRACT_CANCELED_BY_ADMIN;
                sentence = "「" + title + "」 계약이 운영팀에 의해 취소됐어요";
            }
            case "RESEND_HANDLED" -> {
                type = NotificationType.CONTRACT_RESEND_HANDLED;
                sentence = "「" + title + "」 서명 안내가 다시 발송됐어요";
            }
            case "FIXED_FEE_PAID" -> {
                type = NotificationType.CONTRACT_FIXED_FEE_PAID;
                actor = NotificationActorType.BRAND;
                actorName = brand;
                sentence = brand + "가 「" + title + "」 고정 지급비 지급을 기록했어요";
            }
            default -> {
                return;
            }
        }
        publisher.publish(NotificationCommand.toCreator(contract.getCreator().getId(), type)
                .actorType(actor).actorName(actorName).title(sentence)
                .targetType(NotificationTargetType.CONTRACT).targetId(contract.getId())
                .build());
    }

    private static String titleOf(Contract contract) {
        return contract.getTitle() == null || contract.getTitle().isBlank() ? "공구명 미입력" : contract.getTitle();
    }

    private static String creatorName(Contract contract) {
        return contract.getCreator() == null ? null : contract.getCreator().getShowroomName();
    }
}
