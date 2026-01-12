package showroomz.api.admin.user.repository;

import org.springframework.data.jpa.domain.Specification;
import showroomz.api.admin.user.DTO.AdminUserDto;
import showroomz.domain.member.user.entity.Users;

public class UserSpecification {

    public static Specification<Users> search(AdminUserDto.SearchCondition condition) {
        return (root, query, criteriaBuilder) -> {
            Specification<Users> spec = Specification.where(null);

            // 1. 가입 채널 필터
            if (condition.getProviderType() != null) {
                spec = spec.and((root2, query2, cb) ->
                        cb.equal(root2.get("providerType"), condition.getProviderType()));
            }

            // 2. 활동 상태 필터
            if (condition.getStatus() != null) {
                spec = spec.and((root2, query2, cb) ->
                        cb.equal(root2.get("status"), condition.getStatus()));
            }

            // 3. 가입일 기간 필터
            if (condition.getStartDate() != null) {
                spec = spec.and((root2, query2, cb) ->
                        cb.greaterThanOrEqualTo(root2.get("createdAt"), condition.getStartDate().atStartOfDay()));
            }
            if (condition.getEndDate() != null) {
                spec = spec.and((root2, query2, cb) ->
                        cb.lessThanOrEqualTo(root2.get("createdAt"), condition.getEndDate().atTime(23, 59, 59)));
            }

            return spec.toPredicate(root, query, criteriaBuilder);
        };
    }
}
