package showroomz.api.common.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.common.company.dto.CompanyInfoResponse;
import showroomz.global.config.properties.CompanyProperties;

@Service
@RequiredArgsConstructor
public class CommonCompanyInfoService {

    private final CompanyProperties properties;

    public CompanyInfoResponse get() {
        return CompanyInfoResponse.of(properties);
    }
}
