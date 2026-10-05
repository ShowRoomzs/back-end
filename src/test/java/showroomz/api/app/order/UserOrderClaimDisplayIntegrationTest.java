package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.seller.order.SellerOrderTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 내역 · 주문 상세의 반품·교환 표시(C10 설계서 6절 #16 ~ #27). 클레임은 도메인 서비스로 만들고, 검수·재발송·환불
 * API 가 아직 없는 단계는 SQL 로 재현한다 — 여기서 보는 것은 저장된 상태가 화면 값으로 바르게 옮겨지는가다.
 */
@IntegrationTest
class UserOrderClaimDisplayIntegrationTest extends SellerOrderTestSupport {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter APP_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired private OrderClaimService claimService;

    @Test
    @DisplayName("두 항목 중 하나만 반품 신청 — 신청 항목은 「반품 · 진행 중 · 요청」(회색 · 탈색 · 할 일), 나머지는 배송완료. 둘 다 구매확정 예정일이 없다(#16)")
    void partialClaim() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        Long claimId = requestReturn(group, items(group).get(0), 1);
        String due = LocalDate.now().plusDays(7).format(DAY);

        orderList().andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].items[0].status").value("RETURN_IN_PROGRESS"))
                .andExpect(jsonPath("$.content[0].items[0].statusLabel").value("반품"))
                .andExpect(jsonPath("$.content[0].items[0].statusTone").value("MUTED"))
                .andExpect(jsonPath("$.content[0].items[0].dimmed").value(true))
                .andExpect(jsonPath("$.content[0].items[0].statusSub").value("진행 중 · 요청"))
                .andExpect(jsonPath("$.content[0].items[0].claim.claimId").value(claimId))
                .andExpect(jsonPath("$.content[0].items[0].claim.type").value("RETURN"))
                .andExpect(jsonPath("$.content[0].items[0].todo.type").value("REGISTER_COLLECTION_INVOICE"))
                .andExpect(jsonPath("$.content[0].items[0].todo.label").value("회수 송장 등록 필요 · " + due + "까지"))
                .andExpect(jsonPath("$.content[0].items[0].todo.claimId").value(claimId))
                .andExpect(jsonPath("$.content[0].items[0].dates.confirmDueAt").value(nullValue()))
                .andExpect(jsonPath("$.content[0].items[1].status").value("DELIVERED"))
                .andExpect(jsonPath("$.content[0].items[1].statusSub").value(nullValue()))
                .andExpect(jsonPath("$.content[0].items[1].todo").value(nullValue()))
                .andExpect(jsonPath("$.content[0].items[1].dates.confirmDueAt").value(nullValue()));
        // 상세도 같은 조립기를 탄다 — 구매확정 기한 안내가 사라진다.
        detail(group.getOrder().getId())
                .andExpect(jsonPath("$.items[0].status").value("RETURN_IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].todo.claimId").value(claimId))
                .andExpect(jsonPath("$.notices", empty()));
    }

    @Test
    @DisplayName("회수중이 되면 할 일이 사라진다 — 기다리면 되는 구간은 회색 보조 문구만(#25)")
    void collectingHasNoTodo() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = requestReturn(group, items(group).get(0), 1);
        setClaim(claimId, "status = 'COLLECTING'");

        firstItem().andExpect(jsonPath("$.status").value("RETURN_IN_PROGRESS"))
                .andExpect(jsonPath("$.statusSub").value("진행 중 · 회수중"))
                .andExpect(jsonPath("$.todo").value(nullValue()));
    }

    @Test
    @DisplayName("수량 2 중 1개가 환불로 끝나면 배송완료로 돌아오고 returnedQuantity 만 남는다(#17)")
    void partialQuantityRefunded() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(2);
        OrderProduct product = items(group).get(0);
        Long claimId = requestReturn(group, product, 1);
        setClaim(claimId, "status = 'COMPLETED', result = 'REFUNDED', completed_at = CURRENT_TIMESTAMP");
        jdbc.update("UPDATE order_product SET returned_quantity = 1 WHERE order_product_id = ?", product.getId());

        firstItem().andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.returnedQuantity").value(1))
                .andExpect(jsonPath("$.amount").value(2 * CREAM_PRICE))
                .andExpect(jsonPath("$.claim").value(nullValue()))
                .andExpect(jsonPath("$.dimmed").value(false));
    }

    @Test
    @DisplayName("전량 반품 검수 통과 → 환불 집행 — 「반품 · 환불 처리 중」에서 「완료」로, 금액은 예정액에서 집행액으로(#18)")
    void fullReturn() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        OrderProduct product = items(group).get(0);
        Long claimId = requestReturn(group, product, 1);
        setClaim(claimId, "status = 'REFUND_PENDING'");
        jdbc.update("UPDATE order_product SET returned_quantity = 1, status = 'RETURNED' WHERE order_product_id = ?",
                product.getId());

        firstItem().andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.dimmed").value(true))
                .andExpect(jsonPath("$.statusSub").value("환불 처리 중"))
                .andExpect(jsonPath("$.amount").value(CREAM_PRICE))
                .andExpect(jsonPath("$.amountLabel").value("환불 27,200원"))
                .andExpect(jsonPath("$.claim.claimId").value(claimId))
                .andExpect(jsonPath("$.todo").value(nullValue()));

        setClaim(claimId, "status = 'COMPLETED', result = 'REFUNDED', refunded_amount = 24200, "
                + "completed_at = CURRENT_TIMESTAMP");

        firstItem().andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.statusSub").value("완료"))
                .andExpect(jsonPath("$.amount").value(24_200))
                .andExpect(jsonPath("$.amountLabel").value("환불 24,200원"));
    }

    @Test
    @DisplayName("교환 재발송 중 → 도착 — 「교환 · 진행 중 · 재발송」에서 배송완료로 돌아오고 구매확정은 재발송 도착 + 7일이다(#19)")
    void exchange() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = requestReturn(group, items(group).get(0), 1);
        setClaim(claimId, "type = 'EXCHANGE', status = 'RESHIPPING', exchange_option_name = '리필'");

        firstItem().andExpect(jsonPath("$.status").value("EXCHANGE_IN_PROGRESS"))
                .andExpect(jsonPath("$.statusLabel").value("교환"))
                .andExpect(jsonPath("$.statusSub").value("진행 중 · 재발송"))
                .andExpect(jsonPath("$.dimmed").value(true))
                .andExpect(jsonPath("$.claim.type").value("EXCHANGE"))
                .andExpect(jsonPath("$.claim.exchangeOptionName").value("리필"));

        LocalDateTime arrivedAt = LocalDateTime.now().minusHours(2).withNano(0);
        setClaim(claimId, "status = 'COMPLETED', result = 'EXCHANGED', completed_at = CURRENT_TIMESTAMP");
        jdbc.update("UPDATE order_delivery_group SET confirm_restart_at = ? WHERE delivery_group_id = ?", arrivedAt,
                group.getId());

        firstItem().andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.claim").value(nullValue()))
                .andExpect(jsonPath("$.dates.confirmDueAt").value(arrivedAt.plusDays(7).format(APP_TIME)));
    }

    @Test
    @DisplayName("검수 거절 → 결제 필요 → 종결 — 「검수 반려」는 탈색하지 않고, 결제가 필요해지면 할 일이 붙고, 끝나면 배송완료 + 반려 줄(#20 · #25)")
    void rejection() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = requestReturn(group, items(group).get(0), 1);
        Long collectionId = jdbc.queryForObject("SELECT collection_id FROM order_claim WHERE claim_id = ?", Long.class,
                claimId);
        LocalDateTime rejectedAt = LocalDateTime.now().withNano(0);
        setClaim(claimId, "status = 'REJECT_HOLD', rejected_at = '" + rejectedAt.format(APP_TIME) + "'");

        // 같은 박스의 판정이 다 끝나기 전 — 결제를 열지 않는다.
        firstItem().andExpect(jsonPath("$.status").value("RETURN_IN_PROGRESS"))
                .andExpect(jsonPath("$.statusSub").value("검수 반려"))
                .andExpect(jsonPath("$.statusTone").value("MUTED"))
                .andExpect(jsonPath("$.dimmed").value(false))
                .andExpect(jsonPath("$.todo").value(nullValue()))
                // 거절된 클레임은 구매확정을 세우지 않는다 — 예정일을 약속한다(표시 상태가 배송완료가 아니라 날짜 칸은 비지만).
                .andExpect(jsonPath("$.claim.claimStatus").value("REJECT_HOLD"));

        jdbc.update("UPDATE order_claim_collection SET finalized_at = CURRENT_TIMESTAMP WHERE collection_id = ?",
                collectionId);
        LocalDateTime payDue = LocalDateTime.now().plusDays(14).withNano(0);
        jdbc.update("INSERT INTO order_claim_charge (collection_id, type, amount, status, due_at, created_at) "
                + "VALUES (?, 'REJECT_RESHIP', 3000, 'PENDING', ?, CURRENT_TIMESTAMP)", collectionId, payDue);

        firstItem().andExpect(jsonPath("$.statusSub").value("검수 반려"))
                .andExpect(jsonPath("$.todo.type").value("PAY_RESHIP_FEE"))
                .andExpect(jsonPath("$.todo.label").value("재발송 배송비 결제 필요 · " + payDue.format(DAY) + "까지"));

        jdbc.update("UPDATE order_claim_charge SET due_at = ? WHERE collection_id = ?",
                LocalDateTime.now().minusDays(1), collectionId);
        firstItem().andExpect(jsonPath("$.todo.label").value("재발송 배송비 결제 필요"))
                .andExpect(jsonPath("$.todo.dueDate").value(nullValue()));

        setClaim(claimId, "status = 'COMPLETED', result = 'REJECTED', completed_at = CURRENT_TIMESTAMP");

        firstItem().andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.claim").value(nullValue()))
                .andExpect(jsonPath("$.todo").value(nullValue()))
                .andExpect(jsonPath("$.claimRejection.claimId").value(claimId))
                .andExpect(jsonPath("$.claimRejection.type").value("RETURN"))
                .andExpect(jsonPath("$.claimRejection.rejectedAt").value(rejectedAt.format(APP_TIME)))
                .andExpect(jsonPath("$.dates.confirmDueAt").isNotEmpty());
    }

    @Test
    @DisplayName("구매확정된 뒤에도 거절 보류 중인 항목은 「반품 · 검수 반려」로 남고, 종결되면 반려 줄 없이 구매확정이다(#21 · #21-1)")
    void rejectionAfterConfirm() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1, LocalDateTime.now().minusDays(8));
        Long claimId = requestReturn(group, items(group).get(0), 1);
        setClaim(claimId, "status = 'REJECT_HOLD', rejected_at = CURRENT_TIMESTAMP");
        fulfillmentService.confirmIfDue(group.getId(), LocalDateTime.now());

        firstItem().andExpect(jsonPath("$.status").value("RETURN_IN_PROGRESS"))
                .andExpect(jsonPath("$.statusSub").value("검수 반려"));

        setClaim(claimId, "status = 'COMPLETED', result = 'REJECTED', completed_at = CURRENT_TIMESTAMP");

        firstItem().andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.claimRejection").value(nullValue()));
    }

    @Test
    @DisplayName("한 항목에 진행 중 클레임이 둘이면 나중에 신청한 쪽으로 표시한다(#22)")
    void latestClaimWins() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(2);
        OrderProduct product = items(group).get(0);
        requestReturn(group, product, 1);
        Long later = requestReturn(group, product, 1);
        setClaim(later, "type = 'EXCHANGE', status = 'COLLECTING'");

        firstItem().andExpect(jsonPath("$.status").value("EXCHANGE_IN_PROGRESS"))
                .andExpect(jsonPath("$.claim.claimId").value(later))
                .andExpect(jsonPath("$.statusSub").value("진행 중 · 회수중"));
    }

    @Test
    @DisplayName("철회 · 자동 취소된 요청은 흔적을 남기지 않는다 — 배송완료로 돌아오고 반려 줄도 없다(#26)")
    void cancelledClaimLeavesNoTrace() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = requestReturn(group, items(group).get(0), 1);
        claimService.withdraw(claimId, consumer.getId(), LocalDateTime.now());

        firstItem().andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.claim").value(nullValue()))
                .andExpect(jsonPath("$.todo").value(nullValue()))
                .andExpect(jsonPath("$.claimRejection").value(nullValue()))
                .andExpect(jsonPath("$.dates.confirmDueAt").isNotEmpty());
    }

    @Test
    @DisplayName("결제 대기 클레임은 아직 접수 전이다 — 진행 중으로 보이지 않는다(#27)")
    void paymentPendingIsInvisible() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(1);
        Long claimId = requestReturn(group, items(group).get(0), 1);
        setClaim(claimId, "type = 'EXCHANGE', status = 'PAYMENT_PENDING'");

        firstItem().andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.claim").value(nullValue()))
                .andExpect(jsonPath("$.todo").value(nullValue()));
    }

    // ------------------------------------------------------------------ 픽스처

    private OrderDeliveryGroup deliveredGroup(int quantity) throws Exception {
        return deliveredGroup(quantity, LocalDateTime.now().minusHours(1));
    }

    private OrderDeliveryGroup deliveredGroup(int quantity, LocalDateTime deliveredAt) throws Exception {
        return delivered(shipped(prepared(paidGroup(creamVariant, quantity)), "CJ", "684922013378"),
                deliveredAt.withNano(0));
    }

    private OrderDeliveryGroup deliveredTwoItemGroup() throws Exception {
        return delivered(shipped(prepared(paidTwoItemGroup()), "CJ", "684922013378"),
                LocalDateTime.now().minusHours(1).withNano(0));
    }

    private Long requestReturn(OrderDeliveryGroup group, OrderProduct product, int quantity) {
        return claimService.request(new RequestCommand(consumer.getId(), group.getId(), ClaimType.RETURN,
                ClaimReason.CHANGE_OF_MIND, null, List.of(), List.of(new Item(product.getId(), quantity)), null, null),
                LocalDateTime.now()).claimIds().get(0);
    }

    private void setClaim(Long claimId, String assignments) {
        jdbc.update("UPDATE order_claim SET " + assignments + " WHERE claim_id = " + claimId);
    }

    private ResultActions orderList() throws Exception {
        return mockMvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, consumerToken));
    }

    /** 주문 내역 첫 주문의 첫 항목 — 응답을 그 항목으로 좁혀 본다. */
    private ResultActions firstItem() throws Exception {
        String json = orderList().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String item = objectMapper.readTree(json).at("/content/0/items/0").toString();
        return new JsonOnly(item).actions();
    }

    /** 잘라 낸 JSON 조각에 jsonPath 를 걸기 위한 얇은 감싸개. */
    private record JsonOnly(String json) {
        ResultActions actions() {
            org.springframework.mock.web.MockHttpServletResponse response =
                    new org.springframework.mock.web.MockHttpServletResponse();
            response.setCharacterEncoding("UTF-8");
            try {
                response.getWriter().write(json);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            org.springframework.test.web.servlet.MvcResult result = new org.springframework.test.web.servlet.MvcResult() {
                public org.springframework.mock.web.MockHttpServletRequest getRequest() {
                    return new org.springframework.mock.web.MockHttpServletRequest();
                }
                public org.springframework.mock.web.MockHttpServletResponse getResponse() {
                    return response;
                }
                public Object getHandler() { return null; }
                public org.springframework.web.servlet.HandlerInterceptor[] getInterceptors() { return null; }
                public org.springframework.web.servlet.ModelAndView getModelAndView() { return null; }
                public Exception getResolvedException() { return null; }
                public org.springframework.web.servlet.FlashMap getFlashMap() { return null; }
                public Object getAsyncResult() { return null; }
                public Object getAsyncResult(long timeToWait) { return null; }
            };
            return new ResultActions() {
                public ResultActions andExpect(org.springframework.test.web.servlet.ResultMatcher matcher)
                        throws Exception {
                    matcher.match(result);
                    return this;
                }
                public ResultActions andDo(org.springframework.test.web.servlet.ResultHandler handler)
                        throws Exception {
                    handler.handle(result);
                    return this;
                }
                public org.springframework.test.web.servlet.MvcResult andReturn() {
                    return result;
                }
            };
        }
    }
}
