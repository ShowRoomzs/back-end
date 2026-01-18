package showroomz.domain.filter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.filter.entity.CategoryFilter;

import java.util.List;

public interface CategoryFilterRepository extends JpaRepository<CategoryFilter, Long> {
    List<CategoryFilter> findByCategory_CategoryId(Long categoryId);
    void deleteByFilter_Id(Long filterId);
    void deleteByCategory_CategoryId(Long categoryId);
}
