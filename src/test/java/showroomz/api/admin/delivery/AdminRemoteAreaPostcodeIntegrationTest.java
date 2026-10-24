package showroomz.api.admin.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.type.DeliveryRegion;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추가 배송비 권역 우편번호 어드민 등록(55 계획서 P4 · D4) — 등록하면 그 우편번호의 체크아웃이 도서산간 · 추가비 부과, 삭제하면 새 주문부터 일반
 * 권역(결제된 주문의 스냅샷은 그대로) · 입력 오류 400 · 겹침 409 · CSV 일괄 등록(전부 또는 없음).
 */
@DisplayName("55 P4 — 도서산간 우편번호 어드민 등록 · 체크아웃 권역 판정")
class AdminRemoteAreaPostcodeIntegrationTest extends SettlementTestSupport {

    private static final String BASE = "/v1/admin/remote-area-postcodes";
    private static final int REMOTE_SURCHARGE = 5_000;

    @Test
    @DisplayName("등록 → 그 우편번호 체크아웃은 도서산간 · 추가비 부과 · 목록에 보인다 → 삭제하면 새 주문부터 일반 권역 · 결제된 주문은 그대로 · 다시 지우면 404")
    void registerThenCheckout() throws Exception {
        jdbc.update("UPDATE market SET remote_area_surcharge = ? WHERE market_id = ?", REMOTE_SURCHARGE, brand.marketId());
        jdbc.update("UPDATE delivery_address SET zip_code = '40220' WHERE address_id = ?", address.getId());
        OrderDeliveryGroup unregistered = purchase(creamVariant, 1).group();
        assertThat(unregistered.getDeliveryRegion()).isEqualTo(DeliveryRegion.STANDARD); // 등록 전 — 추가비가 부과되지 않던 문제(P4)

        JsonNode created = json(adminPost(BASE, body("40200", "40240", "REMOTE", "  경북 울릉군 "))
                .andExpect(status().isCreated()));
        assertThat(created.get("region").asText()).isEqualTo("REMOTE");
        assertThat(created.get("regionLabel").asText()).isEqualTo("도서산간");
        assertThat(created.get("name").asText()).isEqualTo("경북 울릉군");
        long id = created.get("id").asLong();
        JsonNode list = json(adminGet(BASE).andExpect(status().isOk()));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(list.get(0).get("postcodeTo").asText()).isEqualTo("40240");

        OrderDeliveryGroup remote = purchase(creamVariant, 1).group(); // 27,200 · 무료배송 기준 미달
        assertThat(remote.getDeliveryRegion()).isEqualTo(DeliveryRegion.REMOTE);
        assertThat(remote.getRegionSurcharge()).isEqualTo(REMOTE_SURCHARGE);
        assertThat(remote.getDeliveryFee()).isEqualTo(DELIVERY_FEE + REMOTE_SURCHARGE);

        adminDelete(BASE + "/" + id).andExpect(status().isNoContent());
        assertThat(json(adminGet(BASE))).isEmpty();
        OrderDeliveryGroup afterDelete = purchase(creamVariant, 1).group();
        assertThat(afterDelete.getDeliveryRegion()).isEqualTo(DeliveryRegion.STANDARD);
        assertThat(afterDelete.getRegionSurcharge()).isZero();
        // 운임은 결제 시점 스냅샷 — 이미 결제된 주문은 그대로다.
        OrderDeliveryGroup snapshot = reloadGroup(remote);
        assertThat(snapshot.getDeliveryRegion()).isEqualTo(DeliveryRegion.REMOTE);
        assertThat(snapshot.getRegionSurcharge()).isEqualTo(REMOTE_SURCHARGE);

        adminDelete(BASE + "/" + id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REMOTE_AREA_POSTCODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("입력 오류 400 — 5자리 아님 · 시작 > 끝 · 권역 누락 · STANDARD · 없는 권역 · 이름 100자 초과 · id 형식 / 권한 403")
    void invalidInput() throws Exception {
        adminPost(BASE, body("4020", "40240", "REMOTE", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminPost(BASE, body("40200", "4024a", "REMOTE", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminPost(BASE, body("40240", "40200", "REMOTE", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("시작 우편번호가 끝 우편번호보다 클 수 없습니다."));
        adminPost(BASE, body("40200", "40240", null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminPost(BASE, body("40200", "40240", "STANDARD", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminPost(BASE, body("40200", "40240", "MOON", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminPost(BASE, body("40200", "40240", "REMOTE", "가".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        adminDelete(BASE + "/abc").andExpect(status().isBadRequest());
        assertThat(json(adminGet(BASE))).isEmpty();

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType("application/json").content(toJson(body("40200", "40240", "REMOTE", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("겹침 409 — 등록된 범위와 한 번호라도 겹치면 · 도서산간이 제주 기본 범위와 겹치면 · 제주 범위 등록은 된다 · 맞닿은 범위는 된다")
    void overlapConflicts() throws Exception {
        adminPost(BASE, body("40200", "40240", "REMOTE", "경북 울릉군")).andExpect(status().isCreated());

        adminPost(BASE, body("40240", "40250", "REMOTE", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMOTE_AREA_POSTCODE_OVERLAP"))
                .andExpect(jsonPath("$.message").value("이미 등록된 우편번호 범위와 겹칩니다 — 40200~40240(경북 울릉군)."));
        adminPost(BASE, body("40100", "40300", "REMOTE", null)).andExpect(status().isConflict());
        adminPost(BASE, body("40210", "40210", "JEJU", null)).andExpect(status().isConflict());
        adminPost(BASE, body("40241", "40250", "REMOTE", null)).andExpect(status().isCreated()); // 맞닿음은 겹침이 아니다

        // 제주 우선 — 제주 기본 범위 안을 도서산간으로 등록해도 적용되지 않으므로 막는다. 제주로는 등록된다(운영 시드와 같은 행).
        adminPost(BASE, body("63300", "63365", "REMOTE", "우도")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMOTE_AREA_POSTCODE_OVERLAP"));
        adminPost(BASE, body("63000", "63644", "JEJU", "제주특별자치도")).andExpect(status().isCreated());
        assertThat(json(adminGet(BASE))).extracting(row -> row.get("postcodeFrom").asText())
                .containsExactly("40200", "40241", "63000");
    }

    @Test
    @DisplayName("CSV 일괄 등록 — 머리글 · 빈 줄 건너뜀 · 끝 비면 번호 하나 · 권역 라벨 · 따옴표 이름 · MS949 · 한 행이라도 틀리면 하나도 등록하지 않는다")
    void bulkCsv() throws Exception {
        String csv = "﻿시작,끝,권역,이름\r\n"
                + "40200,40240,REMOTE,경북 울릉군\r\n"
                + "\r\n"
                + "23008,,도서산간,\"인천 옹진군, 백령면\"\r\n"
                + "23100,23136,,\n";
        JsonNode result = json(bulk(csv.getBytes(StandardCharsets.UTF_8)).andExpect(status().isCreated()));
        assertThat(result.get("createdCount").asInt()).isEqualTo(3);
        assertThat(result.at("/items/1/postcodeFrom").asText()).isEqualTo("23008");
        assertThat(result.at("/items/1/postcodeTo").asText()).isEqualTo("23008");
        assertThat(result.at("/items/1/name").asText()).isEqualTo("인천 옹진군, 백령면");
        assertThat(result.at("/items/2/region").asText()).isEqualTo("REMOTE");
        assertThat(result.at("/items/2/name").isNull()).isTrue();
        assertThat(json(adminGet(BASE))).hasSize(3);

        // 엑셀 기본 저장(MS949)도 읽는다 — 머리글 없음
        JsonNode ms949 = json(bulk("59650,59650,도서산간,전남 완도군 청산면".getBytes(Charset.forName("MS949")))
                .andExpect(status().isCreated()));
        assertThat(ms949.at("/items/0/name").asText()).isEqualTo("전남 완도군 청산면");

        // 행 오류 400 · 등록 범위와 겹침 409 · 파일 안 겹침 409 — 어느 경우든 아무것도 등록되지 않는다
        bulk("53000,53001,REMOTE,통영\n5300,53003,REMOTE,오류\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("2행: 우편번호는 5자리 숫자여야 합니다."));
        bulk("53000,53001,REMOTE,통영\n40230,40231,REMOTE,울릉 일부\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMOTE_AREA_POSTCODE_OVERLAP"));
        bulk("53000,53010,REMOTE,통영\n53005,53020,REMOTE,통영 일부\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("2행: 1행과 범위가 겹칩니다."));
        bulk("53000,53001,REMOTE,통영,초과\n".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        bulk("시작,끝,권역,이름\n".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("등록할 행이 없습니다."));
        mockMvc.perform(multipart(BASE + "/bulk").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isBadRequest());
        assertThat(json(adminGet(BASE))).hasSize(4);
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> body(String from, String to, String region, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("postcodeFrom", from);
        body.put("postcodeTo", to);
        body.put("region", region);
        body.put("name", name);
        return body;
    }

    private ResultActions bulk(byte[] content) throws Exception {
        return mockMvc.perform(multipart(BASE + "/bulk")
                .file(new MockMultipartFile("file", "postcodes.csv", "text/csv", content))
                .header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private ResultActions adminDelete(String url) throws Exception {
        return mockMvc.perform(delete(url).header(HttpHeaders.AUTHORIZATION, adminToken));
    }
}
