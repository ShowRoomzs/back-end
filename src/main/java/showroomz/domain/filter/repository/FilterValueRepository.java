package showroomz.domain.filter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.filter.entity.FilterValue;

import java.util.List;

public interface FilterValueRepository extends JpaRepository<FilterValue, Long> {
    List<FilterValue> findByFilter_Id(Long filterId);
}
