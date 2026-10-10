package showroomz.api.common.settlement;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.port.SettlementTaxDocumentStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 증빙 파일 저장소 가짜 — 통합 테스트는 S3 를 부르지 않는다. 올린 바이트를 메모리에 두고 그대로 돌려준다
 * ({@code FakeSettlementPayoutGateway}와 같은 방식 · 테스트 컨텍스트를 가르지 않게 {@code @Primary} 컴포넌트).
 */
@Primary
@Component
public class FakeSettlementTaxDocumentStorage implements SettlementTaxDocumentStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String put(Long settlementId, Long documentId, byte[] bytes) {
        String key = "settlements/" + settlementId + "/tax-document/" + documentId + "-" + UUID.randomUUID() + ".pdf";
        objects.put(key, bytes);
        return key;
    }

    @Override
    public byte[] read(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new IllegalStateException("없는 키: " + key);
        }
        return bytes;
    }

    public int size() {
        return objects.size();
    }
}
