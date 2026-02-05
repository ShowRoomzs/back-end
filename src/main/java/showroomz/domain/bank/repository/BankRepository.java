package showroomz.domain.bank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.bank.entity.Bank;

import java.util.List;

@Repository
public interface BankRepository extends JpaRepository<Bank, String> {
    // 사용 가능한 은행 목록을 순서대로 조회
    List<Bank> findAllByIsActiveTrueOrderByDisplayOrderAsc();
}
