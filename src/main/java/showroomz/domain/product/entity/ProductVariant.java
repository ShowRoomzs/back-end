package showroomz.domain.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product_variant")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "regular_price", nullable = false)
    private Integer regularPrice;

    @Column(name = "sale_price", nullable = false)
    private Integer salePrice;

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "is_representative", nullable = false)
    private Boolean isRepresentative = false;

    @ManyToMany
    @JoinTable(
            name = "variant_option_map",
            joinColumns = @JoinColumn(name = "variant_id"),
            inverseJoinColumns = @JoinColumn(name = "option_id")
    )
    private List<ProductOption> options = new ArrayList<>();

    public ProductVariant(Product product, String name, Integer regularPrice, Integer salePrice, Integer stock, Boolean isRepresentative) {
        this.product = product;
        this.name = name;
        this.regularPrice = regularPrice;
        this.salePrice = salePrice;
        this.stock = stock;
        this.isRepresentative = isRepresentative;
    }
}

