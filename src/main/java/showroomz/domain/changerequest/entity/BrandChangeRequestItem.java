package showroomz.domain.changerequest.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.changerequest.type.ChangeRequestField;

/** {항목, 현재값, 요청값} 1행(§1-2). current_value는 요청 접수 시점의 서버 스냅샷이다. */
@Entity
@Table(name = "brand_change_request_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandChangeRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private BrandChangeRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_key", nullable = false, length = 40)
    private ChangeRequestField fieldKey;

    @Column(name = "current_value")
    private String currentValue;

    @Column(name = "requested_value")
    private String requestedValue;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public BrandChangeRequestItem(ChangeRequestField fieldKey, String currentValue, String requestedValue, int sortOrder) {
        this.fieldKey = fieldKey;
        this.currentValue = currentValue;
        this.requestedValue = requestedValue;
        this.sortOrder = sortOrder;
    }

    void assignRequest(BrandChangeRequest request) {
        this.request = request;
    }
}
