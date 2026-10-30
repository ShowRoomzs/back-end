package showroomz.global.payment.settlement.toss;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 토스 지급대행 v2 요청 · 응답 모양(44 토스 설계서 1-3 · 1-4 · 1-5). 모르는 필드는 버린다 — 토스가 필드를 더해도 깨지지 않게.
 * 요청은 {@code null} 필드를 내보내지 않는다(셀러 수정은 「바꿀 필드만」 · 1-3).
 */
public final class TossPayoutsDtos {

    private TossPayoutsDtos() {
    }

    // ------------------------------------------------------------------ 공통

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(String currency, long value) {
        public static Money krw(long value) {
            return new Money("KRW", value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(String code, String message) {
    }

    /**
     * 목록 — {@code hasMore · nextCursor · items}(부가 API 레퍼런스 셀러 · 지급 목록). req-res 의 리스트 객체 일반 설명은
     * {@code hasNext · lastCursor} 라 별칭으로 둘 다 받는다. 토스가 {@code entityBody} 로 감싸 주면 클라이언트가 벗긴다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page<T>(@JsonAlias("hasNext") Boolean hasMore, @JsonAlias("lastCursor") String nextCursor, List<T> items) {
        public List<T> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }
    }

    // ------------------------------------------------------------------ 잔액(1-5)

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Balance(Money pendingAmount, Money availableAmount) {
        public long available() {
            return availableAmount == null ? 0 : availableAmount.value();
        }
    }

    // ------------------------------------------------------------------ 셀러(1-3)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Company(String name, String representativeName, String businessRegistrationNumber, String email,
                          String phone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Individual(String name, String email, String phone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Account(String bankCode, String accountNumber, String holderName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SellerRequest(String refSellerId, String businessType, Company company, Individual individual,
                                Account account, Map<String, String> metadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Seller(String id, String refSellerId, String businessType, String status, Account account,
                         Map<String, String> metadata) {
    }

    // ------------------------------------------------------------------ 지급(1-4)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PayoutRequest(String refPayoutId, String destination, String scheduleType, String payoutDate,
                                Money amount, String transactionDescription, Map<String, String> metadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payout(String id, String refPayoutId, String destination, String scheduleType, String payoutDate,
                         Money amount, String transactionDescription, String requestedAt, String status,
                         ErrorBody error, Map<String, String> metadata) {
        public long amountValue() {
            return amount == null ? -1 : amount.value();
        }
    }
}
