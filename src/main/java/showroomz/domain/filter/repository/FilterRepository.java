package showroomz.domain.filter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.filter.entity.Filter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FilterRepository extends JpaRepository<Filter, Long> {
    Optional<Filter> findByFilterKey(String filterKey);
    List<Filter> findByFilterKeyIn(Collection<String> filterKeys);
    List<Filter> findByIsActiveTrueOrderBySortOrderAsc();
}
