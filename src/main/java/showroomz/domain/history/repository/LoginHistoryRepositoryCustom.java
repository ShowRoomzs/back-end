package showroomz.domain.history.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.domain.history.entity.LoginHistory;

public interface LoginHistoryRepositoryCustom {
    Page<LoginHistory> search(LoginHistorySearchCondition condition, Pageable pageable);
}
