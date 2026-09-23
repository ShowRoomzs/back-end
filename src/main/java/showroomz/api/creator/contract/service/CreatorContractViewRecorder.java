package showroomz.api.creator.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.repository.ContractRepository;

import java.time.LocalDateTime;

/**
 * 열람 기록({@code creator_viewed_at}) — 상세 GET의 <b>부수 효과</b>다(§27 설계서 5-3).
 *
 * <p>이 값은 파트너 B7(만료 상세)의 「계약서 열람 기록 없음」의 근거다. 만료에는 상대 메모가 없어
 * 그 공백을 이 사실이 메운다.
 *
 * <p><b>왜 {@code POST /{id}/view}를 만들지 않는가</b> — FE가 호출을 빠뜨리거나 순서를 바꾸면
 * 브랜드 화면이 「열람 안 함」으로 거짓말을 한다. 열람 여부는 FE가 선택할 사실이 아니다.
 *
 * <p><b>왜 별도 트랜잭션인가</b> — 상세 조회는 {@code readOnly} 트랜잭션이고 열람 기록은
 * 그 안에서 쓰기를 해야 한다. {@code REQUIRES_NEW}로 분리하면 바깥 조회의 영속성 컨텍스트를
 * 건드리지 않고, <b>실패해도 조회 응답을 깨지 않는다</b> — 열람 기록 때문에 계약서가 안 열리면
 * 본말이 뒤집힌다.
 *
 * <p>예외를 여기서 삼키지 않는다. {@code @Transactional} 메서드 안에서 잡으면 이미
 * rollback-only로 표시된 트랜잭션이 커밋 시점에 {@code UnexpectedRollbackException}으로 다시
 * 터져 나온다. 삼키는 것은 <b>호출자</b>의 몫이다({@code CreatorContractQueryService}).
 *
 * <p>최초 1회 CAS라 멱등이다 — 두 번째 호출부터 0행이고 시각이 덮어써지지 않는다.
 * {@code contract_history}에는 남기지 않는다(같은 절).
 */
@Component
@RequiredArgsConstructor
public class CreatorContractViewRecorder {

    private final ContractRepository contractRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFirstView(Long contractId) {
        contractRepository.markCreatorViewed(contractId, LocalDateTime.now());
    }
}
