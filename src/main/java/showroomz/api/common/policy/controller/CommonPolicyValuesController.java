package showroomz.api.common.policy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.policy.docs.CommonPolicyValuesControllerDocs;
import showroomz.api.common.policy.dto.PolicyValuesResponse;
import showroomz.api.common.policy.service.CommonPolicyValuesService;

@RestController
@RequestMapping("/v1/common/policy-values")
@RequiredArgsConstructor
public class CommonPolicyValuesController implements CommonPolicyValuesControllerDocs {

    private final CommonPolicyValuesService policyValuesService;

    @Override
    @GetMapping
    public ResponseEntity<PolicyValuesResponse> getPolicyValues() {
        return ResponseEntity.ok(policyValuesService.get());
    }
}
