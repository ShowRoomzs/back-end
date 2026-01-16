package showroomz.domain.filter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.filter.entity.CategoryFilterValue;

import java.util.List;

public interface CategoryFilterValueRepository extends JpaRepository<CategoryFilterValue, Long> {
    List<CategoryFilterValue> findByCategoryFilter_Category_CategoryId(Long categoryId);
    void deleteByCategoryFilter_Category_CategoryId(Long categoryId);
}
