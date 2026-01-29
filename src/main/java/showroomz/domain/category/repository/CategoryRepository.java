package showroomz.domain.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import showroomz.domain.category.entity.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryId(Long categoryId);
    Optional<Category> findByName(String name);
    boolean existsByName(String name);
    List<Category> findByParent(Category parent);

    @Query("select c.categoryId as id, c.parent.categoryId as parentId from Category c")
    List<CategoryIdParentId> findAllIdWithParentId();

    interface CategoryIdParentId {
        Long getId();
        Long getParentId();
    }
}

