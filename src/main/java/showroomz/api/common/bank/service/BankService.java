package showroomz.api.common.bank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.common.bank.dto.BankResponse;
import showroomz.domain.bank.repository.BankRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankService {

    private final BankRepository bankRepository;

    public List<BankResponse> getActiveBanks() {
        return bankRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(BankResponse::from)
                .collect(Collectors.toList());
    }
}
