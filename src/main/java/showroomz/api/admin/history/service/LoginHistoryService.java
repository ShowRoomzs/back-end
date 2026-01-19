package showroomz.api.admin.history.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.history.DTO.LocationFilterResponse;
import showroomz.api.admin.history.DTO.LoginHistoryResponse;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.repository.LoginHistoryRepository;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.utils.LocationNameMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    public PageResponse<LoginHistoryResponse> getLoginHistories(LoginHistorySearchCondition condition, PagingRequest pagingRequest) {
        
        Page<LoginHistory> page = loginHistoryRepository.search(condition, pagingRequest.toPageable());

        return PageResponse.of(page.map(LoginHistoryResponse::new));
    }

    /**
     * 필터용 옵션 목록 조회 (국가별 도시 그룹화)
     * @return 국가별로 그룹화된 도시 목록 (한글로 변환)
     */
    public List<LocationFilterResponse> getLocationOptions() {
        List<Object[]> locations = loginHistoryRepository.findDistinctLocations();
        
        // 국가별로 도시를 그룹화 (한글로 변환)
        Map<String, List<String>> countryMap = new LinkedHashMap<>();
        
        for (Object[] loc : locations) {
            String country = (String) loc[0];
            String city = (String) loc[1];
            
            // 영어를 한글로 변환
            String koreanCountry = LocationNameMapper.toKoreanCountry(country);
            String koreanCity = LocationNameMapper.toKoreanCity(city);
            
            countryMap.computeIfAbsent(koreanCountry, k -> new ArrayList<>()).add(koreanCity);
        }
        
        // LocationFilterResponse 리스트로 변환
        return countryMap.entrySet().stream()
                .map(entry -> new LocationFilterResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
