package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.type.PurchaseOrderColumn;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 발주서 컬럼 구성 — 마켓당 1행(34 설계서 1-8). 저장 순서 = 엑셀 좌→우 열 순서다.
 * 기본정보 관리 화면이 같은 값을 수정한다(§34-4 「기본정보 관리에서 수정」).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "market_purchase_order_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_market_purchase_order_template", columnNames = {"market_id"}))
public class MarketPurchaseOrderTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    /** 컬럼 코드 CSV. */
    @Column(name = "columns", nullable = false, length = 500)
    private String columns;

    @Column(name = "updated_by")
    private Long updatedBy;

    private MarketPurchaseOrderTemplate(Market market, String columns, Long updatedBy) {
        this.market = market;
        this.columns = columns;
        this.updatedBy = updatedBy;
    }

    public static MarketPurchaseOrderTemplate of(Market market, List<PurchaseOrderColumn> columnList, Long updatedBy) {
        return new MarketPurchaseOrderTemplate(market, serialize(columnList), updatedBy);
    }

    public void update(List<PurchaseOrderColumn> columnList, Long updatedBy) {
        this.columns = serialize(columnList);
        this.updatedBy = updatedBy;
    }

    public List<PurchaseOrderColumn> columnList() {
        return Arrays.stream(columns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(PurchaseOrderColumn::valueOf)
                .toList();
    }

    private static String serialize(List<PurchaseOrderColumn> columnList) {
        return columnList.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
