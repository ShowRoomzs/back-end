package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

/**
 * 공구 알림 — 파트너센터 · 쇼룸 스튜디오 알림함(1017 설계서 1-4 · 1-5). 어드민 통지는 로그다(어드민 홈 할 일 큐가 맡는다).
 *
 * <p>「내가 한 일은 알리지 않는다」 — 호출 지점이 이미 상대에게만 보내도록 나뉘어 있다(연장 요청 → 인플루언서 · 연장 수락 → 브랜드).
 * 운영자 · 시스템 사건은 양쪽에 간다. 알림 대상이 아닌 이벤트(생성 · 준비완료 · 이슈 · 이행)는 넘긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyNotifier {

    private final NotificationPublisher publisher;

    public void notifyBothParties(GroupBuy groupBuy, String event) {
        notifySeller(groupBuy, event);
        notifyCreator(groupBuy, event);
    }

    public void notifySeller(GroupBuy groupBuy, String event) {
        Line line = sellerLine(groupBuy, event);
        if (line != null) {
            publisher.publish(NotificationCommand.toSeller(groupBuy.getMarket().getId(), line.type())
                    .actorType(line.actor()).actorName(line.actorName()).title(line.sentence())
                    .targetType(NotificationTargetType.GROUP_BUY).targetId(groupBuy.getId()).build());
        }
    }

    public void notifyCreator(GroupBuy groupBuy, String event) {
        Line line = creatorLine(groupBuy, event);
        if (line != null) {
            publisher.publish(NotificationCommand.toCreator(groupBuy.getCreator().getId(), line.type())
                    .actorType(line.actor()).actorName(line.actorName()).title(line.sentence())
                    .targetType(NotificationTargetType.GROUP_BUY).targetId(groupBuy.getId()).build());
        }
    }

    public void notifyAdmin(GroupBuy groupBuy, String event) {
        log.info("[group-buy-notify] to=ADMIN event={} groupBuyId={} number={}",
                event, groupBuy.getId(), groupBuy.getGroupBuyNumber());
    }

    private record Line(NotificationType type, NotificationActorType actor, String actorName, String sentence) {
    }

    private static Line sellerLine(GroupBuy g, String event) {
        String t = "「" + title(g) + "」";
        String creator = g.getCreator().getShowroomName();
        return switch (event) {
            case "POST_SUBMITTED" -> new Line(NotificationType.GROUP_BUY_POST_SUBMITTED, NotificationActorType.CREATOR,
                    creator, t + " 공구 게시물을 등록했습니다 — 운영팀 오픈 승인 대기");
            case "OPEN_APPROVED" -> operator(NotificationType.GROUP_BUY_POST_APPROVED, t + " 게시물 오픈 승인이 완료되었습니다");
            case "OPEN_REJECTED" -> operator(NotificationType.GROUP_BUY_POST_REJECTED,
                    t + " 게시물이 반려되었습니다 — 인플루언서 수정 대기");
            case "POST_HIDDEN" -> operator(NotificationType.GROUP_BUY_POST_HIDDEN, t + " 게시물이 운영팀에 의해 숨김 처리되었습니다");
            case "POST_UNHIDDEN" -> operator(NotificationType.GROUP_BUY_POST_UNHIDDEN, t + " 게시물 숨김이 해제되었습니다");
            case "EXTENSION_ACCEPTED" -> new Line(NotificationType.GROUP_BUY_EXTENSION_ACCEPTED,
                    NotificationActorType.CREATOR, creator, t + " 기간 연장을 수락했습니다");
            case "EXTENSION_REJECTED" -> new Line(NotificationType.GROUP_BUY_EXTENSION_REJECTED,
                    NotificationActorType.CREATOR, creator, t + " 기간 연장을 거절했습니다");
            case "EXTENSION_EXPIRED" -> system(NotificationType.GROUP_BUY_EXTENSION_EXPIRED,
                    t + " 연장 요청이 응답 없이 종료되어 자동 거절되었습니다");
            case "SUSPENSION_REQUESTED" -> new Line(NotificationType.GROUP_BUY_SUSPENSION_REQUESTED,
                    NotificationActorType.CREATOR, creator, t + " 공구 중단을 요청했습니다 — 운영팀 검토 중");
            case "EARLY_CLOSED", "SUSPENDED" -> operator(NotificationType.GROUP_BUY_REQUEST_APPROVED,
                    t + ("EARLY_CLOSED".equals(event) ? " 조기 마감 요청이 승인되었습니다" : " 중단 요청이 승인되었습니다"));
            case "EARLY_CLOSE_REJECTED", "SUSPENSION_REJECTED" -> operator(NotificationType.GROUP_BUY_REQUEST_REJECTED,
                    t + ("EARLY_CLOSE_REJECTED".equals(event) ? " 조기 마감 요청이 반려되었습니다" : " 중단 요청이 반려되었습니다"));
            case "SUSPENSION_NOTICED" -> operator(NotificationType.GROUP_BUY_ADMIN_SUSPENSION_NOTICED,
                    t + " 직권 중단 사전통지가 도착했습니다 — 운영팀 채널에서 소명할 수 있습니다");
            case "SUSPENSION_WITHDRAWN" -> operator(NotificationType.GROUP_BUY_ADMIN_SUSPENSION_WITHDRAWN,
                    t + " 직권 중단 통지가 철회되었습니다");
            case "SUSPENDED_BY_ADMIN", "SUSPENDED_EMERGENCY" -> operator(NotificationType.GROUP_BUY_SUSPENDED_BY_ADMIN,
                    t + " 공구가 운영팀에 의해 중단되었습니다");
            case "OPENED" -> system(NotificationType.GROUP_BUY_OPENED, t + " 공구가 오픈되었습니다");
            case "ENDED" -> system(NotificationType.GROUP_BUY_ENDED, t + " 공구가 종료되었습니다");
            default -> null;
        };
    }

    private static Line creatorLine(GroupBuy g, String event) {
        String t = title(g) + " 공구";
        String brand = g.getMarket().getMarketName();
        return switch (event) {
            case "OPEN_APPROVED" -> operator(NotificationType.GROUP_BUY_POST_APPROVED,
                    t + " 게시물이 승인됐어요 · 시작 시각에 공개돼요");
            case "OPEN_REJECTED" -> operator(NotificationType.GROUP_BUY_POST_REJECTED,
                    t + " 게시물이 반려됐어요 · 사유를 확인해 주세요");
            case "POST_HIDDEN" -> operator(NotificationType.GROUP_BUY_POST_HIDDEN, t + " 게시물이 숨김 처리됐어요");
            case "POST_UNHIDDEN" -> operator(NotificationType.GROUP_BUY_POST_UNHIDDEN, t + " 게시물 숨김이 해제됐어요");
            case "EXTENSION_REQUESTED" -> new Line(NotificationType.GROUP_BUY_EXTENSION_REQUESTED,
                    NotificationActorType.BRAND, brand, brand + "가 " + t + " 기간 연장을 요청했어요");
            case "SUSPENSION_REQUESTED" -> new Line(NotificationType.GROUP_BUY_SUSPENSION_REQUESTED,
                    NotificationActorType.BRAND, brand, brand + "가 " + t + " 중단을 요청했어요 · 운영팀이 검토해요");
            case "EARLY_CLOSE_REQUESTED" -> new Line(NotificationType.GROUP_BUY_EARLY_CLOSE_REQUESTED,
                    NotificationActorType.BRAND, brand, brand + "가 " + t + " 조기 마감을 요청했어요 · 운영팀이 검토해요");
            case "EARLY_CLOSED", "SUSPENDED" -> operator(NotificationType.GROUP_BUY_REQUEST_APPROVED,
                    t + ("EARLY_CLOSED".equals(event) ? "이 조기 마감됐어요" : "이 중단됐어요"));
            case "EARLY_CLOSE_REJECTED", "SUSPENSION_REJECTED" -> operator(NotificationType.GROUP_BUY_REQUEST_REJECTED,
                    t + ("EARLY_CLOSE_REJECTED".equals(event) ? " 조기 마감 요청이 반려됐어요" : " 중단 요청이 반려됐어요"));
            case "SUSPENSION_NOTICED" -> operator(NotificationType.GROUP_BUY_ADMIN_SUSPENSION_NOTICED,
                    t + " 직권 중단 사전통지가 도착했어요");
            case "SUSPENSION_WITHDRAWN" -> operator(NotificationType.GROUP_BUY_ADMIN_SUSPENSION_WITHDRAWN,
                    t + " 직권 중단 통지가 철회됐어요");
            case "SUSPENDED_BY_ADMIN", "SUSPENDED_EMERGENCY" -> operator(NotificationType.GROUP_BUY_SUSPENDED_BY_ADMIN,
                    t + "이 운영팀에 의해 중단됐어요");
            case "OPENED" -> platform(NotificationType.GROUP_BUY_OPENED, t + "이 오픈됐어요 · 판매가 시작됐어요");
            case "ENDED" -> platform(NotificationType.GROUP_BUY_ENDED, t + "이 종료됐어요 · 주문이 끝나면 정산이 생성돼요");
            default -> null;
        };
    }

    private static Line operator(NotificationType type, String sentence) {
        return new Line(type, NotificationActorType.OPERATOR, null, sentence);
    }

    private static Line system(NotificationType type, String sentence) {
        return new Line(type, NotificationActorType.SYSTEM, null, sentence);
    }

    private static Line platform(NotificationType type, String sentence) {
        return new Line(type, NotificationActorType.PLATFORM, null, sentence);
    }

    private static String title(GroupBuy g) {
        String title = g.getContract() == null ? null : g.getContract().getTitle();
        return title == null || title.isBlank() ? g.getGroupBuyNumber() : title;
    }
}
