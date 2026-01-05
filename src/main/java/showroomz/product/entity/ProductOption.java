package showroomz.product.entity;

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
@Table(name = "product_option")
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_group_id", nullable = false)
    private ProductOptionGroup optionGroup;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToMany(mappedBy = "options")
    private List<ProductVariant> variants = new ArrayList<>();

    public ProductOption(ProductOptionGroup optionGroup, String name) {
        this.optionGroup = optionGroup;
        this.name = name;
    }
}

