package showroomz.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 변경 요청 생성은 세 테스트 클래스(파트너센터·어드민·승인 플로우)의 공통 준비 단계다.
 * 본문을 {@code Map}으로 다루는 이유 — {@code fieldKey}에 enum에 없는 값을 넣어
 * 역직렬화 단계 거부(§0 ③)까지 검증해야 하므로 DTO 타입에 갇히면 안 된다.
 */
public class ChangeRequestSteps {

    public static final String EVIDENCE_FILE_URL = "https://cdn.showroomz.test/change-request/evidence.jpg";
    public static final String EVIDENCE_FILE_NAME = "사업자등록증_변경.jpg";
    public static final long EVIDENCE_FILE_SIZE = 1_258_291L;

    private static final String CREATE_URL = "/v1/seller/change-requests";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    ChangeRequestSteps(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** {fieldKey: requestedValue} 순서를 유지한 M1 요청 본문. */
    public static Map<String, Object> businessInfoPayload(String reason, Map<String, String> items) {
        return payload("BUSINESS_INFO", reason, items);
    }

    public static Map<String, Object> settlementPayload(String bankCode, String accountNumber, String accountHolder) {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("BANK_CODE", bankCode);
        items.put("ACCOUNT_NUMBER", accountNumber);
        items.put("ACCOUNT_HOLDER", accountHolder);
        return payload("SETTLEMENT_ACCOUNT", null, items);
    }

    public static Map<String, Object> payload(String type, String reason, Map<String, String> items) {
        List<Map<String, String>> itemList = new ArrayList<>();
        items.forEach((fieldKey, requestedValue) -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("fieldKey", fieldKey);
            item.put("requestedValue", requestedValue);
            itemList.add(item);
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("items", itemList);
        payload.put("reason", reason);
        payload.put("evidenceFileUrl", EVIDENCE_FILE_URL);
        payload.put("evidenceFileName", EVIDENCE_FILE_NAME);
        payload.put("evidenceFileSize", EVIDENCE_FILE_SIZE);
        return payload;
    }

    public ResultActions request(String bearerToken, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post(CREATE_URL)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    /** 생성 성공을 전제로 요청 ID를 돌려준다 — 준비 단계에서 쓴다. */
    public long create(String bearerToken, Map<String, Object> payload) throws Exception {
        String body = request(bearerToken, payload)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("requestId").asLong();
    }

    public long createBusinessInfo(String bearerToken, String reason, Map<String, String> items) throws Exception {
        return create(bearerToken, businessInfoPayload(reason, items));
    }

    public long createSettlement(String bearerToken, String bankCode, String accountNumber, String accountHolder)
            throws Exception {
        return create(bearerToken, settlementPayload(bankCode, accountNumber, accountHolder));
    }
}
