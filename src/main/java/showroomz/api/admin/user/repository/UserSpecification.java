package showroomz.api.admin.user.repository;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import showroomz.api.admin.user.DTO.AdminUserDto;
import showroomz.domain.member.user.entity.Users;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<Users> search(AdminUserDto.SearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. 가입 채널 필터
            if (condition.getProviderType() != null) {
                predicates.add(cb.equal(root.get("providerType"), condition.getProviderType()));
            }

            // 2. 활동 상태 필터
            if (condition.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), condition.getStatus()));
            }

            // 3. 가입일 기간 필터
            if (condition.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), condition.getStartDate().atStartOfDay()));
            }
            if (condition.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), condition.getEndDate().atTime(23, 59, 59)));
            }

            // 모든 조건이 담긴 리스트를 AND 조건으로 묶어서 반환
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}