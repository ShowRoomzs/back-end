package showroomz.api.admin.history.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.history.DTO.LoginHistoryResponse;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.repository.LoginHistoryRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    public PageResponse<LoginHistoryResponse> getLoginHistories(LoginHistorySearchCondition condition, PagingRequest pagingRequest) {
        
        Page<LoginHistory> page = loginHistoryRepository.search(condition, pagingRequest.toPageable());

        return PageResponse.of(page.map(LoginHistoryResponse::new));
    }
}
