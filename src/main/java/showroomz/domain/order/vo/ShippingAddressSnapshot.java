package showroomz.domain.order.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송지 스냅샷 — 주문 시점의 주소를 복사 저장.
 * 소비자 주소록(DeliveryAddress) 변경·삭제와 무관하게 고정되며, 거래기록의 일부로 보존된다.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShippingAddressSnapshot {

    @Column(name = "receiver_name", length = 64)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(name = "delivery_memo", length = 255)
    private String deliveryMemo;
}
