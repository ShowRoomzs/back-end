package showroomz.domain.filter.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.filter.type.FilterCondition;
import showroomz.domain.filter.type.FilterType;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "filter")
public class Filter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "filter_id")
    private Long id;

    @Column(name = "filter_key", nullable = false, unique = true, length = 100)
    private String filterKey;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "filter_type", nullable = false, length = 20)
    private FilterType filterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 10)
    private FilterCondition condition;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "filter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FilterValue> values = new ArrayList<>();
}
