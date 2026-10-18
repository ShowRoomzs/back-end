package showroomz.api.app.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.scenario.OrderFlowTestSupport;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderCancelRequestService;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 46 수정계획 7-1 · 7-4 — 소비자 취소 요청 철회와 취소 상세의 시안 문구. 철회는 브랜드 확인 전(PENDING)에만 되고, 요청 단위이며,
 * PENDING 이 빠지므로 발송 보류가 풀린다. 브랜드 승인 · 거부 · 자동 승인과는 같은 행을 다퉈 한쪽만 이긴다.
 */
@IntegrationTest
class UserCancelWithdrawIntegrationTest extends OrderFlowTestSupport {

    @Autowired private OrderCancelRequestService cancelRequestService;

    @Test
    @DisplayName("[46-7-1] 철회 — 검토 중 상세는 시안 라벨 · 철회 가능 · 안내 문구 → 철회 → 발송 보류 해제(송장 등록 가능) · 취소 상세 없음 · 이력 · 다시 요청 가능")
    void withdrawReleasesShipmentHold() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        OrderProduct item = itemsOf(group).get(0);
        JsonNode requested = json(request(group, item).andExpect(status().isCreated()));
        Long requestId = requested.get("cancelRequestId").asLong();
        assertThat(requested.get("phaseLabel").asText()).isEqualTo("취소 요청 · 브랜드 확인 중");
        assertThat(requested.get("withdrawable").asBoolean()).isTrue();
        assertThat(requested.at("/notices/0").asText()).endsWith("확인 전까지는 요청을 철회할 수 있어요");
        registerShipment(group, "CJ", "400467001001").andExpect(jsonPath("$.succeeded").value(0));

        JsonNode withdrawn = json(withdraw(orderOf(group), requestId).andExpect(status().isOk()));
        assertThat(withdrawn.get("cancelRequestId").asLong()).isEqualTo(requestId);
        assertThat(withdrawn.get("deliveryGroupId").asLong()).isEqualTo(group.getId());
        assertThat(withdrawn.get("orderProductIds")).extracting(JsonNode::asLong).containsExactly(item.getId());
        assertThat(cancelRequestRepository.findById(requestId).orElseThrow().getStatus().name()).isEqualTo("WITHDRAWN");
        assertThat(fulfillmentEvents(group)).contains("CANCEL_REQUEST_WITHDRAWN");
        // 철회한 요청은 취소 내역이 아니다.
        consumerGet("/v1/user/orders/" + orderOf(group) + "/items/" + item.getId() + "/cancel")
                .andExpect(status().isNotFound());

        // 다시 요청할 수 있고, 또 거둘 수 있다.
        Long again = json(request(group, item).andExpect(status().isCreated())).get("cancelRequestId").asLong();
        withdraw(orderOf(group), again).andExpect(status().isOk());
        // 같은 요청을 두 번 거두면 409 — 이미 결정됐다.
        withdraw(orderOf(group), again).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));

        registerShipment(group, "CJ", "400467001002").andExpect(jsonPath("$.succeeded").value(1));
        assertThat(reloadGroup(group).getFulfillmentStatus().name()).isEqualTo("SHIPPING");
    }

    @Test
    @DisplayName("[46-7-1] 경합 — 승인 뒤 철회 409 · 철회 뒤 승인 409 · 자동 승인 배치는 철회 건을 건너뜀 · 거부 상세 라벨 「취소 반려」")
    void withdrawRacesWithBrandDecision() throws Exception {
        OrderDeliveryGroup approvedGroup = preparingGroup();
        Long approvedId = requestId(approvedGroup);
        approveRequest(request(approvedId)).andExpect(status().isOk());
        withdraw(orderOf(approvedGroup), approvedId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));

        OrderDeliveryGroup withdrawnGroup = preparingGroup();
        Long withdrawnId = requestId(withdrawnGroup);
        withdraw(orderOf(withdrawnGroup), withdrawnId).andExpect(status().isOk());
        approveRequest(request(withdrawnId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_REQUEST_ALREADY_DECIDED"));
        assertThat(cancelRequestService.autoApproveIfOverdue(withdrawnId, LocalDateTime.now().plusDays(30))).isFalse();
        assertThat(itemsOf(withdrawnGroup)).allSatisfy(item -> assertThat(item.getStatus().name()).isEqualTo("PAID"));

        OrderDeliveryGroup rejectedGroup = preparingGroup();
        OrderProduct item = itemsOf(rejectedGroup).get(0);
        Long rejectedId = requestId(rejectedGroup);
        rejectRequest(request(rejectedId), "이미 포장했어요").andExpect(status().isOk());
        JsonNode detail = json(consumerGet("/v1/user/orders/" + orderOf(rejectedGroup) + "/items/" + item.getId()
                + "/cancel").andExpect(status().isOk()));
        assertThat(detail.get("phaseLabel").asText()).isEqualTo("취소 반려");
        assertThat(detail.get("withdrawable").asBoolean()).isFalse();
        withdraw(orderOf(rejectedGroup), rejectedId).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("[46-7-1] 남의 주문 · 다른 주문의 요청 · 없는 요청은 404 — 있는지도 알리지 않는다")
    void withdrawOnlyOwnRequest() throws Exception {
        OrderDeliveryGroup group = preparingGroup();
        Long requestId = requestId(group);
        Users other = createConsumer("other-withdraw", "다른 소비자");
        String otherToken = bearerToken(other.getUsername(), RoleType.USER, other.getId());

        mockMvc.perform(post("/v1/user/orders/" + orderOf(group) + "/cancel-requests/" + requestId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isNotFound());
        Long anotherOrder = orderOf(paidGroup());
        withdraw(anotherOrder, requestId).andExpect(status().isNotFound());
        withdraw(orderOf(group), 999_999L).andExpect(status().isNotFound());
        assertThat(cancelRequestRepository.findById(requestId).orElseThrow().getStatus().name()).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------ 도우미

    private Long requestId(OrderDeliveryGroup group) throws Exception {
        return json(request(group, itemsOf(group).get(0)).andExpect(status().isCreated())).get("cancelRequestId").asLong();
    }

    private OrderCancelRequest request(Long requestId) {
        return cancelRequestRepository.findById(requestId).orElseThrow();
    }

    private ResultActions request(OrderDeliveryGroup group, OrderProduct item) throws Exception {
        return mockMvc.perform(post(ORDERS + "/" + orderOf(group) + "/cancel-requests")
                .header(HttpHeaders.AUTHORIZATION, consumerToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("deliveryGroupId", group.getId(), "orderProductIds", List.of(item.getId()),
                        "reasonCode", "CHANGE_OF_MIND"))));
    }

    private ResultActions withdraw(Long orderId, Long requestId) throws Exception {
        return mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel-requests/" + requestId + "/withdraw")
                .header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private ResultActions consumerGet(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    private Long orderOf(OrderDeliveryGroup group) {
        return jdbc.queryForObject("SELECT order_id FROM order_delivery_group WHERE delivery_group_id = ?", Long.class,
                group.getId());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
