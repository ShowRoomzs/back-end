package showroomz.api.app.order.service;

import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.order.entity.Order;

/**
 * 주문 상세 배송지 마스킹(C10 설계서 3-2) — 스크린샷 공유 대비. 앱에서 가리면 원문이 응답에 실려 다니므로 서버가 한다.
 * 어드민 회원 화면의 {@code AdminUserMasker}와는 규칙이 달라 공유하지 않는다.
 */
public final class OrderAddressMasker {

    private static final String DETAIL_MASK = "******";

    private OrderAddressMasker() {
    }

    public static UserOrderDto.MaskedAddress mask(Order order) {
        return UserOrderDto.MaskedAddress.builder()
                .recipientName(maskName(order.getRecipientName()))
                .phoneNumber(maskPhone(order.getRecipientPhone()))
                .address(order.getAddress())
                .detailAddress(maskDetail(order.getDetailAddress()))
                .memo(order.getDeliveryMemo())
                .build();
    }

    /** 마지막 글자 → {@code *}(김수진 → 김수*). 1글자면 그대로. */
    public static String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        return name.substring(0, name.length() - 1) + "*";
    }

    /** 가운데 블록 → {@code ****}(010-****-5678). 하이픈 없는 값은 뒤 4자리만 남긴다. */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String[] blocks = phone.split("-");
        if (blocks.length >= 3) {
            for (int i = 1; i < blocks.length - 1; i++) {
                blocks[i] = "****";
            }
            return String.join("-", blocks);
        }
        if (phone.length() <= 4) {
            return phone;
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    /** 상세 주소는 통째로 가린다 — 비면 null. */
    public static String maskDetail(String detailAddress) {
        return detailAddress == null || detailAddress.isBlank() ? null : DETAIL_MASK;
    }
}
