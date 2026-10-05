package showroomz.api.app.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("주문 상세 배송지 마스킹(C10 설계서 3-2)")
class OrderAddressMaskerTest {

    @Test
    @DisplayName("수취인 — 마지막 글자만 가린다. 1글자·null 은 그대로")
    void name() {
        assertThat(OrderAddressMasker.maskName("김수진")).isEqualTo("김수*");
        assertThat(OrderAddressMasker.maskName("김수")).isEqualTo("김*");
        assertThat(OrderAddressMasker.maskName("김")).isEqualTo("김");
        assertThat(OrderAddressMasker.maskName(null)).isNull();
    }

    @Test
    @DisplayName("연락처 — 가운데 블록을 ****로. 하이픈 없는 값은 뒤 4자리만 남긴다")
    void phone() {
        assertThat(OrderAddressMasker.maskPhone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(OrderAddressMasker.maskPhone("02-123-4567")).isEqualTo("02-****-4567");
        assertThat(OrderAddressMasker.maskPhone("01012345678")).isEqualTo("*******5678");
        assertThat(OrderAddressMasker.maskPhone("5678")).isEqualTo("5678");
        assertThat(OrderAddressMasker.maskPhone(null)).isNull();
    }

    @Test
    @DisplayName("상세 주소 — 통째로 가린다. 비면 null")
    void detailAddress() {
        assertThat(OrderAddressMasker.maskDetail("쇼룸타워 12층 1203호")).isEqualTo("******");
        assertThat(OrderAddressMasker.maskDetail(" ")).isNull();
        assertThat(OrderAddressMasker.maskDetail(null)).isNull();
    }
}
