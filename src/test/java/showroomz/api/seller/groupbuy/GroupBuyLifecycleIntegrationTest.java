package showroomz.api.seller.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.service.ProductGroupBuyStatusSynchronizer;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시간이 여는 전이 — 오픈 · 종료(설계서 3-3). 스케줄러는 테스트에서 꺼 두고 같은 서비스를 직접 부른다.
 */
@DisplayName("[통합] 공구 수명주기 — 자동 오픈 · 자동 종료 · 상품 공구 상태 동기화")
class GroupBuyLifecycleIntegrationTest extends GroupBuyTestSupport {

    @Autowired
    private ProductGroupBuyStatusSynchronizer productSynchronizer;

    @Test
    @DisplayName("시작 시각이 되면 시스템이 연다 — 준비완료만 · 이력 행위자 시스템 · 상품이 팔리기 시작한다")
    void opensReadyGroupBuyAtStart() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.READY);
        LocalDateTime startAt = groupBuy.getStartAt();

        // 시작 전에는 대상이 아니다.
        assertThat(lifecycleService.findIdsToOpen(startAt.minusMinutes(1), 200)).isEmpty();
        assertThat(lifecycleService.open(groupBuy.getId(), startAt.minusMinutes(1))).isFalse();

        LocalDateTime tick = startAt.plusSeconds(30);
        assertThat(lifecycleService.findIdsToOpen(tick, 200)).containsExactly(groupBuy.getId());
        assertThat(lifecycleService.open(groupBuy.getId(), tick)).isTrue();

        GroupBuy opened = reload(groupBuy.getId());
        assertThat(opened.getStatus()).isEqualTo(GroupBuyStatus.IN_PROGRESS);
        assertThat(opened.getOpenedAt()).isEqualTo(tick);
        assertThat(groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId()).getLast())
                .satisfies(entry -> {
                    assertThat(entry.getEventType()).isEqualTo(GroupBuyEventType.OPENED);
                    assertThat(entry.getActorType()).isEqualTo(GroupBuyActorType.SYSTEM);
                });
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.IN_PROGRESS);

        // 두 번 돌아도 이중 전이는 없다 — 조건부 UPDATE가 0행이다.
        assertThat(lifecycleService.open(groupBuy.getId(), tick)).isFalse();
    }

    @Test
    @DisplayName("준비중(게이트 미충족)은 시작 시각이 지나도 열지 않는다 — 상세가 startOverdue로 알린다")
    void preparingIsNotOpenedByTime() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        jdbc.update("UPDATE group_buy SET start_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusHours(1).withNano(0), groupBuy.getId());

        assertThat(lifecycleService.findIdsToOpen(LocalDateTime.now(), 200)).isEmpty();
        detail(groupBuy.getId()).andExpect(jsonPath("$.timeline.startOverdue").value(true));
    }

    @Test
    @DisplayName("종료 시각이 되면 끝난다 — 종결 시각은 tick이 아니라 종료 예정 · 대기 연장 만료 · 검토 중 요청·통지 LAPSED")
    void endsAndClosesEverythingPending() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        action(groupBuy.getId(), "extension-request", Map.of("extensionDays", 3)).andExpect(status().isOk());
        GroupBuyChangeRequest pending = seedPendingRequest(groupBuy.getId(), ChangeRequestType.EARLY_CLOSE,
                GroupBuyActorType.SELLER, "STOCK_OUT");
        GroupBuyAdminSuspension notice = seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(1).withNano(0));

        // 요청들 뒤의 시각으로 종료 예정을 당겨 온다 — 스케줄러는 그보다 늦게 돈다.
        LocalDateTime endAt = LocalDateTime.now().plusSeconds(1).withNano(0);
        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?", endAt, groupBuy.getId());
        LocalDateTime tick = endAt.plusSeconds(40);

        assertThat(lifecycleService.findIdsToEnd(tick, 200)).containsExactly(groupBuy.getId());
        assertThat(lifecycleService.end(groupBuy.getId(), tick)).isTrue();

        GroupBuy ended = reload(groupBuy.getId());
        assertThat(ended.getStatus()).isEqualTo(GroupBuyStatus.ENDED);
        assertThat(ended.getCloseType()).isEqualTo(GroupBuyCloseType.COMPLETED);
        assertThat(ended.getEndedAt()).isEqualTo(endAt);
        assertThat(ended.getFulfillmentDueAt()).isEqualTo(endAt.plusDays(3));

        GroupBuyExtensionRequest extension = extensionRequestRepository.findByGroupBuyId(groupBuy.getId()).orElseThrow();
        assertThat(extension.getStatus()).isEqualTo(ExtensionRequestStatus.EXPIRED);
        assertThat(extension.getResponseActorType()).isEqualTo(GroupBuyActorType.SYSTEM);
        assertThat(changeRequestRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ChangeRequestStatus.LAPSED);
        assertThat(adminSuspensionRepository.findById(notice.getId()).orElseThrow().getStatus())
                .isEqualTo(AdminSuspensionStatus.LAPSED);

        detail(groupBuy.getId())
                .andExpect(jsonPath("$.history[-2:].eventType").value(contains("EXTENSION_EXPIRED", "ENDED")))
                .andExpect(jsonPath("$.extension.status").value("EXPIRED"))
                .andExpect(jsonPath("$.extension.responseActorType").value("SYSTEM"))
                .andExpect(jsonPath("$.activeRequest").doesNotExist())
                .andExpect(jsonPath("$.permissions.canCheckFulfillment").value(true));
        // 끝난 공구는 상품을 붙들지 않는다.
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.NOT_CONNECTED);
    }

    @Test
    @DisplayName("한 상품이 두 공구에 걸리면 하나가 끝나도 다른 공구 단계로 재계산한다 — 무조건 연결 해제가 아니다")
    void productStatusIsRecomputedAcrossGroupBuys() {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuy upcoming = seedIn(GroupBuyStatus.READY);
        // 적재가 SQL로 상태를 옮겼으니 동기화를 한 번 태운다 — 가장 앞선 단계(진행중)가 이긴다.
        transactionTemplate.executeWithoutResult(tx ->
                productSynchronizer.resync(groupBuyRepository.findById(upcoming.getId()).orElseThrow()));
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.IN_PROGRESS);

        jdbc.update("UPDATE group_buy SET end_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusMinutes(1).withNano(0), selling.getId());
        assertThat(lifecycleService.end(selling.getId(), LocalDateTime.now())).isTrue();

        // 진행중 공구가 끝났지만 같은 상품을 담은 준비완료 공구가 남아 있다.
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.READY);
    }

    @Test
    @DisplayName("무응답 자동 이행은 기본 꺼짐이다 — 기한이 지나도 대상이 없다(D-1 약관 근거 없음)")
    void autoConfirmIsOffByDefault() {
        GroupBuy ended = seedIn(GroupBuyStatus.ENDED);
        jdbc.update("UPDATE group_buy SET fulfillment_due_at = ? WHERE group_buy_id = ?",
                LocalDateTime.now().minusDays(1).withNano(0), ended.getId());

        assertThat(lifecycleService.findIdsToAutoConfirm(LocalDateTime.now(), 200)).isEmpty();
        assertThat(lifecycleService.autoConfirmFulfillment(ended.getId(), LocalDateTime.now())).isZero();
    }
}
