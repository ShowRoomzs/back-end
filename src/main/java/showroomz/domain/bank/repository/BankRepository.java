package showroomz.domain.bank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.bank.entity.Bank;

import java.util.List;

@Repository
public interface BankRepository extends JpaRepository<Bank, String> {
    // 사용 가능한 은행 목록을 순서대로 조회
    List<Bank> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    /** 회원 정보는 은행 이름만 저장한다(가입 때 {@code bank.getName()}) — PG 파트너 등록이 코드로 되돌릴 때(포트원 설계서 4-5). */
    java.util.Optional<Bank> findByName(String name);
}
