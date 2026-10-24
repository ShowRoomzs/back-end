package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.DeliveryRegion;

import java.time.LocalDateTime;

/** 추가 배송비 권역 우편번호 범위(1021 설계서 3-1) — 운영 등록. 제주 기본 범위는 코드에도 있다({@code DeliveryRegionResolver}). */
@Entity
@Table(name = "remote_area_postcode")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemoteAreaPostcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "postcode_id")
    private Long id;

    @Column(name = "postcode_from", nullable = false, columnDefinition = "char(5)")
    private String postcodeFrom;

    @Column(name = "postcode_to", nullable = false, columnDefinition = "char(5)")
    private String postcodeTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 16)
    private DeliveryRegion region;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static RemoteAreaPostcode of(String from, String to, DeliveryRegion region, String name, LocalDateTime now) {
        RemoteAreaPostcode p = new RemoteAreaPostcode();
        p.postcodeFrom = from;
        p.postcodeTo = to;
        p.region = region;
        p.name = name;
        p.createdAt = now;
        return p;
    }
}
