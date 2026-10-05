package showroomz.api.seller.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerBlockedException;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.TrackSnapshot;
import showroomz.global.scheduler.ClaimTrackingScheduler;
import showroomz.support.IntegrationTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클레임 추적(보강 시나리오 3절 TR-01 ~ TR-09). 통합 테스트 컨텍스트는 추적 연동을 꺼 두어 배치 빈이 없다 — 주문 추적
 * 배치 테스트({@code OrderFulfillmentSchedulerIntegrationTest})처럼 실제 서비스 빈과 테스트용 포트로 배치를 직접 조립해
 * {@code tick()}을 부른다. 반영 단건은 도메인 진입점을 직접 부른다.
 */
@IntegrationTest
class ClaimTrackingIntegrationTest extends ClaimTestSupport {

    // ------------------------------------------------------------------ 배치 루프(TR-01 ~ TR-04)

    @Test
    @DisplayName("[TR-01 · TR-04] 대상이 페이지 크기를 넘어도 한 회차에 전량을 id 순으로 한 번씩 돈다 — 회수는 회수 중만, 재발송은 재발송 중만")
    void sweepsAllTargetsOnce() throws Exception {
        List<String> collecting = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String invoice = newInvoice();
            returnClaimWith(invoice);
            collecting.add(invoice);
        }
        // 대상이 아닌 것 — 송장 없는 회수 대기 · 브랜드 도착(입고 확인) · 재발송 대기
        returnClaimWithoutInvoice();
        received(returnClaim(deliveredGroup(creamVariant, 1)));
        ProductVariant refill = addSamePriceVariant(creamVariant, 10);
        passed(exchangeClaim(deliveredGroup(creamVariant, 1), refill));
        // 재발송 중 1건
        Long reshipping = passed(exchangeClaim(deliveredGroup(creamVariant, 1), refill));
        String reshipInvoice = newInvoice();
        registerReship(reshipping, "CJ", reshipInvoice).andExpect(jsonPath("$.succeeded").value(1));

        StubTracker tracker = new StubTracker(number -> Optional.empty());
        scheduler(tracker, 2).tick();

        List<String> expected = new ArrayList<>(collecting);
        expected.add(reshipInvoice);
        assertThat(tracker.calls).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("[TR-02] 키 단위 차단이면 그 회차를 접는다 — 남은 회수 건도 재발송 폴링도 돌지 않는다")
    void blockedStopsRound() throws Exception {
        String first = newInvoice();
        String second = newInvoice();
        returnClaimWith(first);
        returnClaimWith(second);
        ProductVariant refill = addSamePriceVariant(creamVariant, 10);
        Long reshipping = passed(exchangeClaim(deliveredGroup(creamVariant, 1), refill));
        registerReship(reshipping, "CJ", newInvoice()).andExpect(jsonPath("$.succeeded").value(1));

        StubTracker tracker = new StubTracker(number -> {
            throw new DeliveryTrackerBlockedException("이용권 만료");
        });
        scheduler(tracker, 300).tick();

        assertThat(tracker.calls).containsExactly(first);
        assertThat(collectingInvoices()).containsExactly(first, second);
    }

    @Test
    @DisplayName("[TR-03] 한 건의 조회가 터져도 나머지는 반영된다 — 터진 건은 회수 중 그대로")
    void failureIsIsolated() throws Exception {
        String ok1 = newInvoice();
        String broken = newInvoice();
        String ok2 = newInvoice();
        Long first = returnClaimWith(ok1);
        Long failing = returnClaimWith(broken);
        Long third = returnClaimWith(ok2);
        LocalDateTime arrivedAt = LocalDateTime.now().minusHours(1).withNano(0);

        StubTracker tracker = new StubTracker(number -> {
            if (number.equals(broken)) {
                throw new IllegalStateException("연동 업체 장애");
            }
            return Optional.of(snapshotOf(arrivedAt, arrivedAt, scan(arrivedAt, "브랜드", "배송완료", 6)));
        });
        scheduler(tracker, 300).tick();

        assertThat(claimStatus(first)).isEqualTo("ARRIVED");
        assertThat(claimStatus(third)).isEqualTo("ARRIVED");
        assertThat(claimStatus(failing)).isEqualTo("COLLECTING");
    }

    // ------------------------------------------------------------------ 반영 단건(TR-05 ~ TR-09)

    @Test
    @DisplayName("[TR-05] 회수 송장을 정정한 뒤 구 송장의 결과가 오면 덮지 않는다 — 상태 · 최종 추적 시각 불변")
    void staleCollectionInvoiceIgnored() throws Exception {
        String old = newInvoice();
        Long claimId = returnClaimWith(old);
        Long collectionId = collectionIdOf(claimId);
        mockMvcPutInvoice(claimId, newInvoice());

        LocalDateTime at = LocalDateTime.now().minusHours(1).withNano(0);
        claimService.applyCollectionTracking(collectionId, DeliveryCarrier.CJ, old,
                snapshotOf(at, at, scan(at, "브랜드", "배송완료", 6)), LocalDateTime.now());

        assertThat(claimStatus(claimId)).isEqualTo("COLLECTING");
        assertThat(collectionRow(collectionId).get("last_tracking_at")).isNull();
        assertThat(collectionRow(collectionId).get("arrived_at")).isNull();
    }

    @Test
    @DisplayName("[TR-06] 같은 도착 결과를 두 번 반영해도 도착 이력은 한 번 · 스캔 이력은 중복 없이 쌓인다")
    void idempotentArrival() throws Exception {
        String invoice = newInvoice();
        Long claimId = returnClaimWith(invoice);
        LocalDateTime at = LocalDateTime.now().minusHours(1).withNano(0);
        TrackSnapshot snapshot = snapshotOf(at, at, scan(at.minusHours(5), "강남", "집화처리", 2),
                scan(at, "브랜드", "배송완료", 6));

        for (int i = 0; i < 2; i++) {
            claimService.applyCollectionTracking(collectionIdOf(claimId), DeliveryCarrier.CJ, invoice, snapshot,
                    LocalDateTime.now());
        }

        assertThat(events(claimId)).filteredOn("ARRIVED"::equals).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_tracking_event WHERE tracking_number = ?",
                Integer.class, invoice)).isEqualTo(2);
    }

    @Test
    @DisplayName("[TR-07] 브랜드가 먼저 입고 확인한 뒤 추적이 도착을 감지하면 — 상태는 그대로 · 도착 시각만 적고 도착 이력은 없다")
    void arrivalAfterReceive() throws Exception {
        String invoice = newInvoice();
        Long claimId = received(returnClaimWith(invoice));
        LocalDateTime at = LocalDateTime.now().minusHours(1).withNano(0);

        claimService.applyCollectionTracking(collectionIdOf(claimId), DeliveryCarrier.CJ, invoice,
                snapshotOf(at, at, scan(at, "브랜드", "배송완료", 6)), LocalDateTime.now());

        assertThat(claimStatus(claimId)).isEqualTo("RECEIVED");
        assertThat(collectionRow(collectionIdOf(claimId)).get("arrived_at")).isNotNull();
        assertThat(events(claimId)).doesNotContain("ARRIVED");
    }

    @Test
    @DisplayName("[TR-08] 한 박스에서 철회한 항목은 도착 감지에 끌려가지 않는다 — 회수 중인 항목만 도착으로")
    void withdrawnItemNotArrived() throws Exception {
        OrderDeliveryGroup group = deliveredTwoItemGroup();
        RequestResult request = requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), null);
        Long kept = request.claimIds().get(0);
        Long withdrawn = request.claimIds().get(1);
        userPost(USER_CLAIMS + "/" + withdrawn + "/withdraw", Map.of()).andExpect(status().isOk());
        String invoice = newInvoice();
        mockMvcPutInvoice(kept, invoice);

        LocalDateTime at = LocalDateTime.now().minusHours(1).withNano(0);
        claimService.applyCollectionTracking(request.collectionId(), DeliveryCarrier.CJ, invoice,
                snapshotOf(at, at, scan(at, "브랜드", "배송완료", 6)), LocalDateTime.now());

        assertThat(claimStatus(kept)).isEqualTo("ARRIVED");
        assertThat(claimRow(withdrawn)).containsEntry("status", "COMPLETED").containsEntry("result", "CANCELLED");
    }

    @Test
    @DisplayName("[TR-09] 스캔 이력 없이 마지막 이벤트 시각만 오면 — 최종 추적 시각만 갱신 · 앱 회수 조회는 여전히 「아직 조회되지 않아요」")
    void lastEventWithoutScans() throws Exception {
        String invoice = newInvoice();
        Long claimId = returnClaimWith(invoice);
        LocalDateTime at = LocalDateTime.now().minusHours(2).withNano(0);

        claimService.applyCollectionTracking(collectionIdOf(claimId), DeliveryCarrier.CJ, invoice,
                snapshotOf(at, null), LocalDateTime.now());

        assertThat(collectionRow(collectionIdOf(claimId)).get("last_tracking_at")).isNotNull();
        userGet(USER_CLAIMS + "/" + claimId + "/collection-tracking")
                .andExpect(jsonPath("$.trackable").value(false))
                .andExpect(jsonPath("$.headline.text").value("아직 조회되지 않아요"));
    }

    // ------------------------------------------------------------------ 보조

    private Long returnClaimWith(String invoice) throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        return requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), invoice)
                .claimIds().get(0);
    }

    private void returnClaimWithoutInvoice() throws Exception {
        OrderDeliveryGroup group = deliveredGroup(creamVariant, 1);
        requestClaim(group, ClaimType.RETURN, ClaimReason.CHANGE_OF_MIND, allItems(group), null);
    }

    private List<String> collectingInvoices() {
        return jdbc.queryForList("SELECT k.tracking_number FROM order_claim_collection k WHERE EXISTS ("
                + "SELECT 1 FROM order_claim c WHERE c.collection_id = k.collection_id AND c.status = 'COLLECTING') "
                + "ORDER BY k.collection_id", String.class);
    }

    private void mockMvcPutInvoice(Long claimId, String trackingNumber) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(USER_CLAIMS + "/" + claimId + "/collection-invoice")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("carrier", "CJ", "trackingNumber", trackingNumber))))
                .andExpect(status().isOk());
    }

    private ClaimTrackingScheduler scheduler(DeliveryTrackerPort tracker, int batchSize) {
        DeliveryTrackerProperties properties = new DeliveryTrackerProperties();
        properties.setBatchSize(batchSize);
        properties.setCallGapMs(0);
        return new ClaimTrackingScheduler(claimService, tracker, properties);
    }

    private static final class StubTracker implements DeliveryTrackerPort {
        private final Function<String, Optional<TrackSnapshot>> answer;
        private final List<String> calls = new ArrayList<>();

        private StubTracker(Function<String, Optional<TrackSnapshot>> answer) {
            this.answer = answer;
        }

        @Override
        public ValidationResult validateInvoice(DeliveryCarrier carrier, String trackingNumber) {
            return ValidationResult.UNAVAILABLE;
        }

        @Override
        public Optional<TrackSnapshot> track(DeliveryCarrier carrier, String trackingNumber) {
            calls.add(trackingNumber);
            return answer.apply(trackingNumber);
        }
    }
}
