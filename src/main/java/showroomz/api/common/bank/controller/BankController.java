package showroomz.api.common.bank.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.bank.dto.BankResponse;
import showroomz.api.common.bank.service.BankService;
import showroomz.api.common.docs.CommonBankControllerDocs;

import java.util.List;

@RestController
@RequestMapping("/v1/common/banks")
@RequiredArgsConstructor
public class BankController implements CommonBankControllerDocs {

    private final BankService bankService;

    @Override
    @GetMapping
    public List<BankResponse> getBanks() {
        return bankService.getActiveBanks();
    }
}
