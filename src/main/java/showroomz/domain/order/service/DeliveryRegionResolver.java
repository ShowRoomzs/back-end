package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.entity.RemoteAreaPostcode;
import showroomz.domain.order.repository.RemoteAreaPostcodeRepository;
import showroomz.domain.order.type.DeliveryRegion;

import java.util.List;

/**
 * 배송 권역 판정(1021 설계서 3-2) — 우편번호(5자리)로 제주 · 도서산간을 가른다. 두 권역이 겹치면 제주가 우선이다(파트너 06).
 *
 * <p>제주(63000 ~ 63644)는 코드가 안다 — 등록 데이터가 비어도 제주 추가비는 빠지지 않는다. 도서산간은 운영 등록
 * ({@code remote_area_postcode})만 본다. 우편번호를 모르면 일반이다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryRegionResolver {

    static final String JEJU_FROM = "63000";
    static final String JEJU_TO = "63644";

    private final RemoteAreaPostcodeRepository postcodeRepository;

    public DeliveryRegion resolve(String zipCode) {
        String postcode = normalize(zipCode);
        if (postcode == null) {
            return DeliveryRegion.STANDARD;
        }
        if (postcode.compareTo(JEJU_FROM) >= 0 && postcode.compareTo(JEJU_TO) <= 0) {
            return DeliveryRegion.JEJU;
        }
        List<RemoteAreaPostcode> covering = postcodeRepository.findCovering(postcode);
        if (covering.stream().anyMatch(p -> p.getRegion() == DeliveryRegion.JEJU)) {
            return DeliveryRegion.JEJU;
        }
        return covering.isEmpty() ? DeliveryRegion.STANDARD : DeliveryRegion.REMOTE;
    }

    /** 그 권역의 편도 추가비 — 마켓 설정. 일반이면 0. */
    public static int surchargeOf(Market market, DeliveryRegion region) {
        if (market == null || region == null) {
            return 0;
        }
        return switch (region) {
            case JEJU -> market.getJejuSurcharge() == null ? 0 : market.getJejuSurcharge();
            case REMOTE -> market.getRemoteAreaSurcharge() == null ? 0 : market.getRemoteAreaSurcharge();
            case STANDARD -> 0;
        };
    }

    private static String normalize(String zipCode) {
        if (zipCode == null) {
            return null;
        }
        String digits = zipCode.replaceAll("[^0-9]", "");
        return digits.length() == 5 ? digits : null;
    }
}
