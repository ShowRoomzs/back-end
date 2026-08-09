package showroomz.api.seller.basicinfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementAccountMaskerTest {

    @Test
    @DisplayName("뒤 6자리만 노출하고 나머지는 * 로 치환한다")
    void masksAllButLastSixDigits() {
        String masked = SettlementAccountMasker.mask("000123456789");

        assertThat(masked).hasSize(12);
        assertThat(masked).endsWith("456789");
        assertThat(masked).startsWith("******");
    }

    @Test
    @DisplayName("길이가 6자리 이하면 전체를 마스킹한다")
    void masksEntireValueWhenShorterThanSuffix() {
        assertThat(SettlementAccountMasker.mask("1234")).isEqualTo("****");
    }

    @Test
    @DisplayName("null 입력은 null을 반환한다")
    void returnsNullForNullInput() {
        assertThat(SettlementAccountMasker.mask(null)).isNull();
    }
}
