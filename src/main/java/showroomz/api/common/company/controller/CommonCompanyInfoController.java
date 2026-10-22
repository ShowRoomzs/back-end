package showroomz.api.common.company.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.company.docs.CommonCompanyInfoControllerDocs;
import showroomz.api.common.company.dto.CompanyInfoResponse;
import showroomz.api.common.company.service.CommonCompanyInfoService;

@RestController
@RequestMapping("/v1/common/company-info")
@RequiredArgsConstructor
public class CommonCompanyInfoController implements CommonCompanyInfoControllerDocs {

    private final CommonCompanyInfoService companyInfoService;

    @Override
    @GetMapping
    public ResponseEntity<CompanyInfoResponse> getCompanyInfo() {
        return ResponseEntity.ok(companyInfoService.get());
    }
}
