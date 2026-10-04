package showroomz.api.seller.order;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.CancelRequestReason;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderTab;
import showroomz.support.IntegrationTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파트너센터 주문 조회(34 설계서 4-1 ~ 4-3) — 탭 ↔ 조회 조건은 서버가 소유한다(1-3). 탭이 기본 기간·기본 정렬까지 갖고,
 * 취소 요청은 상태가 아니라 PENDING 행의 존재(오버레이)로 탭을 옮긴다(0-2). 요약 바와 탭 카운트는 같은 응답에서
 * 같은 데이터를 읽는다(4-2).
 */
@IntegrationTest
class SellerOrderQueryIntegrationTest extends SellerOrderTestSupport {

    @Nested
    @DisplayName("탭 · 요약")
    class Tabs {

        @Test
        @DisplayName("탭은 이행 상태 + 취소 요청 오버레이로 나뉜다 — 결제 전 주문은 어느 탭에도 없다")
        void tabsSplitByStatusAndPendingCancel() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup requested = paidGroup();
            seedCancelRequest(requested);
            OrderDeliveryGroup preparing = preparingGroup();
            OrderDeliveryGroup shipping = shippingGroup("100020003000");
            OrderDeliveryGroup cancelled = paidGroup();
            directCancel(List.of(cancelled.getId()), "SOLD_OUT", "품절로 발송이 어렵습니다.").andExpect(status().isOk());
            placeCardOrder(creamVariant, 1); // 결제 대기 — 그룹 PENDING(화면 밖)

            expectTab("ALL", fresh, requested, preparing, shipping, cancelled);
            expectTab("NEW", fresh);
            expectTab("PREPARING", preparing);
            expectTab("CANCEL_REQUESTED", requested);
            expectTab("SHIPPING", shipping);
            expectTab("CANCELLED", cancelled);
            expectTab("RETURNING");
            expectTab("DELIVERED");
            expectTab("CONFIRMED");
        }

        @Test
        @DisplayName("요약 바 — 작업 큐는 취소 요청 건을 빼고 · 배송 이상은 배지 + 반송중 합산 · 모듈 밖 칸은 null")
        void summaryActionBar() throws Exception {
            paidGroup();
            seedCancelRequest(paidGroup());
            preparingGroup();
            OrderDeliveryGroup alerted = shippingGroup("100020003001");
            backdateShippedAt(alerted, LocalDateTime.now().minusHours(25));
            track(alerted, null, LocalDateTime.now());
            returning(shippingGroup("100020003002"));
            shippingGroup("100020003003"); // 배지 없는 배송중 — 배송 이상 아님
            placeCardOrder(creamVariant, 1);

            sellerGet(SELLER_ORDERS + "/summary")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.actionBar.prepareStart").value(1))
                    .andExpect(jsonPath("$.actionBar.invoiceRegister").value(1))
                    .andExpect(jsonPath("$.actionBar.deliveryIssue").value(2))
                    // 반품·교환 모듈 전 — 0 은 「처리할 일이 없다」는 거짓 정보다(설계서 0-6).
                    .andExpect(jsonPath("$.actionBar.incomingCheck").value(nullValue()))
                    .andExpect(jsonPath("$.actionBar.reshipExchange").value(nullValue()))
                    .andExpect(jsonPath("$.tabCounts.ALL").value(6))
                    .andExpect(jsonPath("$.tabCounts.NEW").value(1))
                    .andExpect(jsonPath("$.tabCounts.PREPARING").value(1))
                    .andExpect(jsonPath("$.tabCounts.CANCEL_REQUESTED").value(1))
                    .andExpect(jsonPath("$.tabCounts.SHIPPING").value(2))
                    .andExpect(jsonPath("$.tabCounts.RETURNING").value(1))
                    .andExpect(jsonPath("$.tabCounts.DELIVERED").value(0))
                    .andExpect(jsonPath("$.tabCounts.CONFIRMED").value(0))
                    .andExpect(jsonPath("$.tabCounts.CANCELLED").value(0));
        }

        @Test
        @DisplayName("탭 카운트 9종은 같은 탭의 목록 건수와 일치한다 — 카운트와 목록이 다른 조건을 보면 결함")
        void tabCountsMatchListTotals() throws Exception {
            paidGroup();
            seedCancelRequest(preparingGroup());
            preparingGroup();
            shippingGroup("100020003004");
            OrderDeliveryGroup cancelled = paidGroup();
            directCancel(List.of(cancelled.getId()), "DEFECT", "하자 확인").andExpect(status().isOk());

            JsonNode counts = json(sellerGet(SELLER_ORDERS + "/summary")).get("tabCounts");
            for (OrderTab tab : OrderTab.values()) {
                long listed = json(sellerGet(SELLER_ORDERS + "?tab=" + tab.name()))
                        .get("pageInfo").get("totalResults").asLong();
                assertThat(listed).as("탭 %s", tab).isEqualTo(counts.get(tab.name()).asLong());
            }
        }

        @Test
        @DisplayName("검색 결과가 없어도 요약 바는 전체 기준이라 숫자가 유지된다(§34-2)")
        void summaryIgnoresSearch() throws Exception {
            paidGroup();

            sellerGet(SELLER_ORDERS + "?tab=NEW&searchType=ORDER_NUMBER&keyword=NO-SUCH-ORDER")
                    .andExpect(jsonPath("$.content", empty()));
            sellerGet(SELLER_ORDERS + "/summary")
                    .andExpect(jsonPath("$.actionBar.prepareStart").value(1));
        }

        @Test
        @DisplayName("정의되지 않은 탭·정렬 값은 400 INVALID_INPUT")
        void unknownEnumIsBadRequest() throws Exception {
            sellerGet(SELLER_ORDERS + "?tab=DRAFT")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            sellerGet(SELLER_ORDERS + "?sort=PRICE_DESC")
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("기간 · 조회 기준 · 정렬 · 검색 · 페이징")
    class Filters {

        @Test
        @DisplayName("기본 기간은 탭이 소유한다 — 작업 큐 7일 · 조회 탭 1개월")
        void defaultPeriodPerTab() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup eightDaysAgo = paidGroup();
            backdatePaidAt(eightDaysAgo, now.minusDays(8));
            OrderDeliveryGroup thirtyOneDaysAgo = paidGroup();
            backdatePaidAt(thirtyOneDaysAgo, now.minusDays(31));
            OrderDeliveryGroup today = paidGroup();

            expectTab("NEW", today);
            expectTab("ALL", today, eightDaysAgo);
            expectIds(sellerGet(SELLER_ORDERS + "?tab=NEW&from=" + LocalDate.now().minusDays(40)),
                    today, eightDaysAgo, thirtyOneDaysAgo);
        }

        @Test
        @DisplayName("조회 기간 상한 1년 — 365일은 되고 366일은 400 ORDER_SEARCH_RANGE_EXCEEDED")
        void rangeLimitIsOneYear() throws Exception {
            LocalDate today = LocalDate.now();

            sellerGet(SELLER_ORDERS + "?from=" + today.minusDays(365) + "&to=" + today)
                    .andExpect(status().isOk());
            sellerGet(SELLER_ORDERS + "?from=" + today.minusDays(366) + "&to=" + today)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORDER_SEARCH_RANGE_EXCEEDED"));
            // to 를 생략하면 오늘까지로 본다.
            sellerGet(SELLER_ORDERS + "?from=" + today.minusDays(366))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORDER_SEARCH_RANGE_EXCEEDED"));
        }

        @Test
        @DisplayName("조회 기준 5종은 각 단계 시각 컬럼으로 거른다 — 그 단계에 오지 않은 건은 빠진다")
        void dateBasisFiltersByStageTimestamp() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();
            OrderDeliveryGroup shipping = shippingGroup("100020003005");

            expectIds(sellerGet(SELLER_ORDERS + "?dateBasis=PAID"), fresh, preparing, shipping);
            expectIds(sellerGet(SELLER_ORDERS + "?dateBasis=PREPARE_STARTED"), preparing, shipping);
            expectIds(sellerGet(SELLER_ORDERS + "?dateBasis=SHIPPED"), shipping);
            expectIds(sellerGet(SELLER_ORDERS + "?dateBasis=DELIVERED"));
            expectIds(sellerGet(SELLER_ORDERS + "?dateBasis=CONFIRMED"));
        }

        @Test
        @DisplayName("작업 큐는 오래된순 · 조회 탭은 최신순(결제일) — sort 파라미터가 오면 그것을 따른다")
        void defaultSortPerTab() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup oldest = paidGroup();
            backdatePaidAt(oldest, now.minusDays(3));
            OrderDeliveryGroup middle = paidGroup();
            backdatePaidAt(middle, now.minusDays(1));
            OrderDeliveryGroup newest = paidGroup();

            expectOrder(sellerGet(SELLER_ORDERS + "?tab=NEW"), oldest, middle, newest);
            expectOrder(sellerGet(SELLER_ORDERS + "?tab=ALL"), newest, middle, oldest);
            expectOrder(sellerGet(SELLER_ORDERS + "?tab=NEW&sort=LATEST_FIRST"), newest, middle, oldest);
            expectOrder(sellerGet(SELLER_ORDERS + "?tab=ALL&sort=OLDEST_FIRST"), oldest, middle, newest);
        }

        @Test
        @DisplayName("발송기한 임박순(SHIP_DUE_ASC) — 저장된 발송기한 스냅샷 기준")
        void sortByShipDue() throws Exception {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            OrderDeliveryGroup late = paidGroup();
            backdateShipDueAt(late, now.plusDays(3));
            OrderDeliveryGroup soonest = paidGroup();
            backdateShipDueAt(soonest, now.plusHours(2));
            OrderDeliveryGroup middle = paidGroup();
            backdateShipDueAt(middle, now.plusDays(1));

            expectOrder(sellerGet(SELLER_ORDERS + "?tab=NEW&sort=SHIP_DUE_ASC"), soonest, middle, late);
        }

        @Test
        @DisplayName("검색 4타입 — 주문번호(하위주문번호 포함) · 수취인 · 송장번호(정제 후) · 상품명(항목 EXISTS)")
        void searchFourTypes() throws Exception {
            OrderDeliveryGroup renamed = paidGroup();
            jdbc.update("UPDATE orders SET recipient_name = ? WHERE order_id = ?", "이도윤", renamed.getOrder().getId());
            OrderDeliveryGroup twoItems = paidTwoItemGroup();
            OrderDeliveryGroup shipping = shippingGroup("555566667777");

            expectIds(search("ORDER_NUMBER", orderNumberOf(renamed)), renamed);
            expectIds(search("ORDER_NUMBER", twoItems.getSubOrderNumber()), twoItems);
            expectIds(search("RECIPIENT_NAME", "도윤"), renamed);
            expectIds(search("TRACKING_NUMBER", "5555-6666"), shipping);
            expectIds(search("PRODUCT_NAME", "세럼"), twoItems);
            expectIds(search("PRODUCT_NAME", "크림"), renamed, twoItems, shipping);
            // 빈 키워드는 검색 조건으로 쓰지 않는다.
            expectIds(search("RECIPIENT_NAME", "  "), renamed, twoItems, shipping);
        }

        @Test
        @DisplayName("페이징 — page 는 1부터 · pageInfo 가 전체 건수·다음 페이지를 알려 준다")
        void paging() throws Exception {
            paidGroup();
            paidGroup();
            paidGroup();

            sellerGet(SELLER_ORDERS + "?tab=NEW&page=1&size=2")
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.pageInfo.currentPage").value(1))
                    .andExpect(jsonPath("$.pageInfo.totalPages").value(2))
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(3))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(true));
            sellerGet(SELLER_ORDERS + "?tab=NEW&page=2&size=2")
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.pageInfo.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("목록 행")
    class ListRow {

        @Test
        @DisplayName("행은 탭 공통 superset — 「상품명 외 N건」 · 항목 미리보기 · 결제금액 · 정산 null · 연락처/주소는 싣지 않는다")
        void rowCarriesSupersetWithoutContact() throws Exception {
            OrderDeliveryGroup group = paidTwoItemGroup();
            assertThat(group.getDeliveryFee()).isEqualTo(DELIVERY_FEE); // 문턱 100,000 — 51,200 은 유료배송

            sellerGet(SELLER_ORDERS + "?tab=NEW")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumberOf(group)))
                    .andExpect(jsonPath("$.content[0].subOrderNumber").value(orderNumberOf(group) + "-01"))
                    .andExpect(jsonPath("$.content[0].groupBuyName").value("글로우 크림 앵콜 공구"))
                    .andExpect(jsonPath("$.content[0].recipientName").value("김수민"))
                    .andExpect(jsonPath("$.content[0].productSummary").value(endsWith(" 외 1건")))
                    .andExpect(jsonPath("$.content[0].totalQuantity").value(2))
                    .andExpect(jsonPath("$.content[0].statusLabel").value("신규(준비 대기)"))
                    .andExpect(jsonPath("$.content[0].statusTone").value("WARNING"))
                    .andExpect(jsonPath("$.content[0].orderedAt").value(notNullValue()))
                    .andExpect(jsonPath("$.content[0].shipDueAt").value(notNullValue()))
                    .andExpect(jsonPath("$.content[0].paidAmount").value(CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE))
                    .andExpect(jsonPath("$.content[0].settlementLabel").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].cancelRequest").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].overlays.cancelRequested").value(false))
                    .andExpect(jsonPath("$.content[0].overlays.trackingAlert").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].items.length()").value(2))
                    .andExpect(jsonPath("$.content[0].items[*].price", containsInAnyOrder(CREAM_PRICE, SERUM_PRICE)))
                    .andExpect(jsonPath("$.content[0].items[*].itemStatusLabel",
                            contains("신규(준비 대기)", "신규(준비 대기)")))
                    .andExpect(jsonPath("$.content[0].items[*].cancelled", contains(false, false)))
                    // 연락처·주소·배송 요청사항은 상세·발주서에만(§34-11).
                    .andExpect(jsonPath("$.content[0].recipient").doesNotExist())
                    .andExpect(jsonPath("$.content[0].phone").doesNotExist())
                    .andExpect(jsonPath("$.content[0].address").doesNotExist())
                    .andExpect(jsonPath("$.content[0].deliveryMemo").doesNotExist());
        }

        @Test
        @DisplayName("발송기한 경과는 파생값 — 작업 큐 상태에서만 켜지고, 송장이 등록된 건은 기한이 지나도 꺼져 있다")
        void shipOverdueOnlyForWorkQueue() throws Exception {
            LocalDateTime past = LocalDateTime.now().minusHours(1);
            OrderDeliveryGroup overdue = paidGroup();
            backdateShipDueAt(overdue, past);
            OrderDeliveryGroup onTime = paidGroup();
            OrderDeliveryGroup shippedLate = shippingGroup("100020003006");
            backdateShipDueAt(shippedLate, past);

            sellerGet(SELLER_ORDERS + "?tab=NEW&sort=SHIP_DUE_ASC")
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(overdue.getId()))
                    .andExpect(jsonPath("$.content[0].overlays.shipOverdue").value(true))
                    .andExpect(jsonPath("$.content[1].deliveryGroupId").value(onTime.getId()))
                    .andExpect(jsonPath("$.content[1].overlays.shipOverdue").value(false));
            sellerGet(SELLER_ORDERS + "?tab=SHIPPING")
                    .andExpect(jsonPath("$.content[0].overlays.shipOverdue").value(false));
            orderDetail(overdue.getId())
                    .andExpect(jsonPath("$.overlays.shipOverdue").value(true));
        }

        @Test
        @DisplayName("취소 요청 행 — 사유·경과·「N건 중 M건 요청 · 남은 K건 발송 대기」 · 요청 항목만 경고 표시")
        void cancelRequestSummaryInRow() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            seedCancelRequest(group, List.of(itemOf(group, creamVariant)), CancelRequestReason.ETC,
                    "사이즈를 잘못 골랐어요", LocalDateTime.now().withNano(0).minusHours(5));

            sellerGet(SELLER_ORDERS + "?tab=CANCEL_REQUESTED")
                    .andExpect(jsonPath("$.content[0].status").value("PREPARING")) // 이행 상태는 그대로(0-2)
                    .andExpect(jsonPath("$.content[0].overlays.cancelRequested").value(true))
                    .andExpect(jsonPath("$.content[0].cancelRequest.reasonLabel").value("기타"))
                    .andExpect(jsonPath("$.content[0].cancelRequest.reasonDetail").value("사이즈를 잘못 골랐어요"))
                    .andExpect(jsonPath("$.content[0].cancelRequest.elapsedHours").value(5))
                    .andExpect(jsonPath("$.content[0].cancelRequest.summary").value("2건 중 1건 요청 · 남은 1건 발송 대기"))
                    .andExpect(jsonPath("$.content[0].items[?(@.productName == '글로우 크림 50ml')].cancelRequested",
                            contains(true)))
                    .andExpect(jsonPath("$.content[0].items[?(@.productName == '글로우 세럼 30ml')].cancelRequested",
                            contains(false)));
        }
    }

    @Nested
    @DisplayName("상세")
    class Detail {

        @Test
        @DisplayName("상세는 배송지 마스킹을 풀고 결제수단·공구·금액 요약·발송기한을 싣는다")
        void detailUnmasksRecipient() throws Exception {
            OrderDeliveryGroup group = paidTwoItemGroup();

            orderDetail(group.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber").value(orderNumberOf(group)))
                    .andExpect(jsonPath("$.subOrderNumber").value(orderNumberOf(group) + "-01"))
                    .andExpect(jsonPath("$.paymentMethod").value("카드"))
                    .andExpect(jsonPath("$.groupBuyId").value(groupBuy.getId()))
                    .andExpect(jsonPath("$.groupBuyName").value("글로우 크림 앵콜 공구"))
                    .andExpect(jsonPath("$.recipient.name").value("김수민"))
                    .andExpect(jsonPath("$.recipient.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.recipient.zipCode").value("06234"))
                    .andExpect(jsonPath("$.recipient.address").value("서울 강남구 테헤란로 000"))
                    .andExpect(jsonPath("$.recipient.detailAddress").value("쇼룸타워 12층"))
                    .andExpect(jsonPath("$.recipient.deliveryMemo").value("문 앞에 놓아주세요"))
                    .andExpect(jsonPath("$.amounts.productTotal").value(CREAM_PRICE + SERUM_PRICE))
                    .andExpect(jsonPath("$.amounts.deliveryFee").value(DELIVERY_FEE))
                    .andExpect(jsonPath("$.amounts.totalAmount").value(CREAM_PRICE + SERUM_PRICE + DELIVERY_FEE))
                    .andExpect(jsonPath("$.amounts.cancelRequestedAmount").value(nullValue()))
                    .andExpect(jsonPath("$.amounts.cancelledAmount").value(0))
                    .andExpect(jsonPath("$.timeline.shipDueAt").value(notNullValue()))
                    .andExpect(jsonPath("$.timeline.prepareStartedAt").value(nullValue()))
                    .andExpect(jsonPath("$.cancelRequest").value(nullValue()))
                    .andExpect(jsonPath("$.history.length()").value(1))
                    .andExpect(jsonPath("$.history[0].eventType").value("PAID"))
                    .andExpect(jsonPath("$.history[0].label").value("결제완료 · 신규 진입"))
                    .andExpect(jsonPath("$.history[0].actorType").value("SYSTEM"))
                    .andExpect(jsonPath("$.history[0].actorLabel").value("시스템"));
        }

        @Test
        @DisplayName("처리 이력은 최신순 · 주체 라벨 · 송장 등록에는 「형식 검증 생략(연동 전)」이 남는다")
        void historyNewestFirst() throws Exception {
            OrderDeliveryGroup group = shippingGroup("1234-5678-9012");

            orderDetail(group.getId())
                    .andExpect(jsonPath("$.history[*].eventType",
                            contains("INVOICE_REGISTERED", "PREPARE_STARTED", "PAID")))
                    .andExpect(jsonPath("$.history[0].actorLabel").value("브랜드"))
                    .andExpect(jsonPath("$.history[0].detail").value("CJ대한통운 123456789012 · 형식 검증 생략(연동 전)"))
                    .andExpect(jsonPath("$.history[1].label").value("준비 시작 · 소비자 취소권 종료"))
                    .andExpect(jsonPath("$.timeline.prepareStartedAt").value(notNullValue()))
                    .andExpect(jsonPath("$.timeline.shippedAt").value(notNullValue()))
                    .andExpect(jsonPath("$.timeline.carrier").value("CJ"))
                    .andExpect(jsonPath("$.timeline.carrierLabel").value("CJ대한통운"))
                    .andExpect(jsonPath("$.timeline.trackingNumber").value("123456789012"));
        }

        @Test
        @DisplayName("가능한 액션은 서버가 내린다 — 상태·취소 요청에 따라 버튼 노출이 갈린다(§34-3)")
        void actionsFollowStatus() throws Exception {
            OrderDeliveryGroup fresh = paidGroup();
            OrderDeliveryGroup preparing = preparingGroup();
            OrderDeliveryGroup shipping = shippingGroup("100020003007");
            OrderDeliveryGroup requested = preparingGroup();
            seedCancelRequest(requested);
            OrderDeliveryGroup cancelled = paidGroup();
            directCancel(List.of(cancelled.getId()), "SOLD_OUT", "품절").andExpect(status().isOk());
            OrderDeliveryGroup delivered = delivered(shippingGroup("100020003008"), LocalDateTime.now().withNano(0));

            // canPrepareStart · canRegisterInvoice · canUpdateInvoice · canCancelDirectly · canDecideCancelRequest
            expectActions(fresh, true, false, false, true, false);
            expectActions(preparing, false, true, false, true, false);
            expectActions(shipping, false, false, true, false, false);
            expectActions(requested, false, false, false, false, true);
            expectActions(cancelled, false, false, false, false, false);
            expectActions(delivered, false, false, false, false, false);
        }

        @Test
        @DisplayName("취소 요청 블록 — 요청 당시 상태 · 준비 시작 후 경과 · 요청분 별 행 · 남은 항목 수")
        void cancelRequestBlock() throws Exception {
            OrderDeliveryGroup group = preparingTwoItemGroup();
            seedCancelRequest(group, List.of(itemOf(group, creamVariant)), CancelRequestReason.ORDER_MISTAKE, null,
                    LocalDateTime.now().withNano(0));

            orderDetail(group.getId())
                    .andExpect(jsonPath("$.cancelRequest.reasonLabel").value("주문 실수"))
                    .andExpect(jsonPath("$.cancelRequest.statusAtRequestLabel").value("상품준비중"))
                    .andExpect(jsonPath("$.cancelRequest.hoursSincePrepareStart").value(0))
                    .andExpect(jsonPath("$.cancelRequest.items.length()").value(1))
                    .andExpect(jsonPath("$.cancelRequest.items[0].productName").value("글로우 크림 50ml"))
                    .andExpect(jsonPath("$.cancelRequest.items[0].refundAmount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.cancelRequest.totalRefundAmount").value(CREAM_PRICE))
                    .andExpect(jsonPath("$.cancelRequest.remainingItemCount").value(1))
                    .andExpect(jsonPath("$.amounts.cancelRequestedAmount").value(CREAM_PRICE));
        }

        @Test
        @DisplayName("준비 시작 전에 들어온 취소 요청은 「준비 시작 후 경과」가 null 이다")
        void cancelRequestBeforePreparation() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            seedCancelRequest(group);

            orderDetail(group.getId())
                    .andExpect(jsonPath("$.cancelRequest.statusAtRequestLabel").value("신규(준비 대기)"))
                    .andExpect(jsonPath("$.cancelRequest.hoursSincePrepareStart").value(nullValue()))
                    .andExpect(jsonPath("$.cancelRequest.remainingItemCount").value(0));
        }

        @Test
        @DisplayName("없는 하위주문은 404 ORDER_GROUP_NOT_FOUND")
        void unknownGroupIsNotFound() throws Exception {
            orderDetail(999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        }

        @Test
        @DisplayName("결제 전(PENDING) 하위주문은 셀러 화면 밖이다 — 목록·요약에 없고 상세도 열리지 않는다")
        void unpaidGroupIsOutOfScreen() throws Exception {
            Created unpaid = placeCardOrder(creamVariant, 1);
            OrderDeliveryGroup pending = onlyGroupOf(unpaid.orderId());

            expectTab("ALL");
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.ALL").value(0));
            orderDetail(pending.getId())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_GROUP_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("보강 — 입력 경계 · 발송기한 스냅샷 · 데이터 계약(Q)")
    class Boundaries {

        @Test
        @DisplayName("[Q-01] 시작일이 종료일보다 늦으면 400 INVALID_INPUT — 빈 목록으로 「주문이 없다」고 답하지 않는다")
        void reversedRangeIsBadRequest() throws Exception {
            paidGroup();
            LocalDate today = LocalDate.now();

            sellerGet(SELLER_ORDERS + "?from=" + today + "&to=" + today.minusDays(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("조회 시작일은 종료일보다 늦을 수 없습니다."));
            // 같은 날은 정상 범위다.
            sellerGet(SELLER_ORDERS + "?from=" + today + "&to=" + today)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("[Q-02] 검색 타입만 · 검색어만 · 공백 검색어는 검색 조건을 무시한다 — 400 아님 · 탭 전체와 같은 건수")
        void incompleteSearchIsIgnored() throws Exception {
            OrderDeliveryGroup first = paidGroup();
            OrderDeliveryGroup second = paidGroup();

            expectIds(mockMvc.perform(get(SELLER_ORDERS).header(HttpHeaders.AUTHORIZATION, brandToken)
                    .param("searchType", "ORDER_NUMBER")), first, second);
            expectIds(mockMvc.perform(get(SELLER_ORDERS).header(HttpHeaders.AUTHORIZATION, brandToken)
                    .param("keyword", orderNumberOf(first))), first, second);
            expectIds(search("RECIPIENT_NAME", "   "), first, second);
        }

        @Test
        @DisplayName("[Q-03] page 1 미만은 첫 페이지로 보정 · size 는 1~100 밖이면 400 INVALID_INPUT(500 아님)")
        void pagingBoundaries() throws Exception {
            paidGroup();

            for (String page : List.of("0", "-3")) {
                sellerGet(SELLER_ORDERS + "?page=" + page)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.pageInfo.currentPage").value(1))
                        .andExpect(jsonPath("$.content.length()").value(1));
            }
            for (String size : List.of("0", "-1", "101")) {
                sellerGet(SELLER_ORDERS + "?size=" + size)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            }
            sellerGet(SELLER_ORDERS + "?size=100").andExpect(status().isOk());
            sellerGet(SELLER_ORDERS + "?size=1").andExpect(status().isOk());
        }

        @Test
        @DisplayName("[Q-04] 출고 소요일이 비어 있는 마켓 — 발송기한 null · 경과 없음 · 임박순 정렬에서 마지막 · 상세도 200")
        void nullShippingLeadDays() throws Exception {
            jdbc.update("UPDATE market SET shipping_lead_days = NULL WHERE market_id = ?", brand.marketId());
            OrderDeliveryGroup noDue = paidGroup();
            jdbc.update("UPDATE market SET shipping_lead_days = ? WHERE market_id = ?", SHIPPING_LEAD_DAYS,
                    brand.marketId());
            OrderDeliveryGroup withDue = paidGroup();
            assertThat(noDue.getShipDueAt()).isNull();

            expectOrder(sellerGet(SELLER_ORDERS + "?tab=NEW&sort=SHIP_DUE_ASC"), withDue, noDue);
            sellerGet(SELLER_ORDERS + "?tab=NEW&sort=SHIP_DUE_ASC")
                    .andExpect(jsonPath("$.content[1].shipDueAt").value(nullValue()))
                    .andExpect(jsonPath("$.content[1].overlays.shipOverdue").value(false));
            orderDetail(noDue.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timeline.shipDueAt").value(nullValue()))
                    .andExpect(jsonPath("$.overlays.shipOverdue").value(false));
        }

        @Test
        @DisplayName("[Q-05] 발송기한은 결제 시점 스냅샷 — 마켓 출고 소요일을 바꿔도 기존 하위주문은 그대로 · 새 결제만 새 값")
        void shipDueAtIsSnapshot() throws Exception {
            OrderDeliveryGroup before = paidGroup();
            jdbc.update("UPDATE market SET shipping_lead_days = ? WHERE market_id = ?", 5, brand.marketId());
            OrderDeliveryGroup after = paidGroup();

            assertThat(reload(before).getShipDueAt())
                    .isEqualTo(before.getOrder().getPaidAt().plusDays(SHIPPING_LEAD_DAYS));
            assertThat(after.getShipDueAt()).isEqualTo(after.getOrder().getPaidAt().plusDays(5));
            orderDetail(before.getId())
                    .andExpect(jsonPath("$.timeline.shipDueAt").value(jsonTime(before.getShipDueAt())));
        }

        @Test
        @DisplayName("[Q-06] 같은 브랜드 하위주문 2개인 주문 — 목록 2행 · 탭 카운트 2 · 소비자 전액 취소는 둘 다 취소(CONSUMER)")
        void sameBrandTwoGroupsCancelledTogether() throws Exception {
            OrderDeliveryGroup first = paidGroup();
            OrderDeliveryGroup second = addSecondGroup(first);

            expectTab("NEW", first, second);
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.NEW").value(2));
            sellerGet(SELLER_ORDERS + "?tab=NEW")
                    .andExpect(jsonPath("$.content[*].subOrderNumber", containsInAnyOrder(
                            orderNumberOf(first) + "-01", orderNumberOf(first) + "-02")));

            cancel(first.getOrder().getId()).andExpect(status().isOk());

            for (OrderDeliveryGroup group : List.of(first, second)) {
                OrderDeliveryGroup cancelled = reload(group);
                assertThat(cancelled.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
                assertThat(cancelled.getCancelType()).isEqualTo(OrderCancelType.CONSUMER);
            }
            sellerGet(SELLER_ORDERS + "/summary").andExpect(jsonPath("$.tabCounts.CANCELLED").value(2));
        }

        @Test
        @DisplayName("[Q-06] 같은 브랜드 하위주문 중 하나만 준비 시작해도 소비자 전액 취소는 닫힌다 — 나머지는 NEW 그대로")
        void sameBrandOnePreparedClosesConsumerCancel() throws Exception {
            OrderDeliveryGroup first = paidGroup();
            OrderDeliveryGroup second = addSecondGroup(first);
            prepareStart(first.getId()).andExpect(jsonPath("$.succeeded").value(1));

            cancel(first.getOrder().getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));
            assertThat(reload(second).getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NEW);
        }

        @Test
        @DisplayName("[Q-07] 공구가 없는 하위주문(백필 행) — 목록·상세의 공구명 null · 200")
        void groupWithoutGroupBuy() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            jdbc.update("UPDATE order_delivery_group SET group_buy_id = NULL WHERE delivery_group_id = ?", group.getId());

            sellerGet(SELLER_ORDERS + "?tab=NEW")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].deliveryGroupId").value(group.getId()))
                    .andExpect(jsonPath("$.content[0].groupBuyName").value(nullValue()));
            orderDetail(group.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupBuyId").value(nullValue()))
                    .andExpect(jsonPath("$.groupBuyName").value(nullValue()));
        }

        @Test
        @DisplayName("[Q-08] 결제 행을 찾을 수 없는 주문(백필) — 상세의 결제수단 null · 500 아님")
        void detailWithoutPaymentRow() throws Exception {
            OrderDeliveryGroup group = paidGroup();
            jdbc.update("UPDATE orders SET paid_payment_id = ? WHERE order_id = ?", "missing-payment",
                    group.getOrder().getId());

            orderDetail(group.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentMethod").value(nullValue()));
        }
    }

    // ------------------------------------------------------------------ 보조

    private void expectTab(String tab, OrderDeliveryGroup... expected) throws Exception {
        expectIds(sellerGet(SELLER_ORDERS + "?tab=" + tab), expected);
    }

    private ResultActions search(String searchType, String keyword) throws Exception {
        return mockMvc.perform(get(SELLER_ORDERS).header(HttpHeaders.AUTHORIZATION, brandToken)
                .param("tab", "ALL").param("searchType", searchType).param("keyword", keyword));
    }

    private void expectIds(ResultActions result, OrderDeliveryGroup... expected) throws Exception {
        result.andExpect(status().isOk());
        if (expected.length == 0) {
            result.andExpect(jsonPath("$.content", empty()));
            return;
        }
        result.andExpect(jsonPath("$.content[*].deliveryGroupId", containsInAnyOrder(ids(expected))));
    }

    private void expectOrder(ResultActions result, OrderDeliveryGroup... expected) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].deliveryGroupId", contains(ids(expected))));
    }

    private void expectActions(OrderDeliveryGroup group, boolean prepareStart, boolean registerInvoice,
                               boolean updateInvoice, boolean cancelDirectly, boolean decideCancelRequest)
            throws Exception {
        orderDetail(group.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.canPrepareStart").value(prepareStart))
                .andExpect(jsonPath("$.actions.canRegisterInvoice").value(registerInvoice))
                .andExpect(jsonPath("$.actions.canUpdateInvoice").value(updateInvoice))
                .andExpect(jsonPath("$.actions.canCancelDirectly").value(cancelDirectly))
                .andExpect(jsonPath("$.actions.canDecideCancelRequest").value(decideCancelRequest));
    }

    private static Object[] ids(OrderDeliveryGroup... groups) {
        return Arrays.stream(groups).map(g -> g.getId().intValue()).toArray();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
