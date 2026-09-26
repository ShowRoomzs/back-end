package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.api.admin.contract.service.ContractDraftGenerator.Submission;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.type.*;
import showroomz.global.error.exception.*;

/**
 * 운영자 계약서 다운로드. 캐시(이 제출본의 생성본)가 있으면 그대로 주고, 없으면 만들어서 준다.
 *
 * <p>운영자는 파일을 받아야 하므로 응답은 렌더링을 기다린다. 대신 <b>렌더링 동안 트랜잭션·계약 행 잠금을
 * 잡지 않는다</b> — 판정과 저장만 짧게 잠근다({@link ContractDraftGenerator}의 세 단계). 예전에는 잠금을 쥔 채
 * 렌더링해서 그동안 같은 계약의 승인·반려·취소가 전부 줄을 섰다.
 */
@Service
@RequiredArgsConstructor
public class ContractDraftService {
    private final AdminContractAccess access;
    private final ContractDraftGenerator generator;
    private final ContractDocumentStorage storage;

    public DownloadResponse download(Long id, Long operator) {
        String actor = access.operatorName(operator);

        // ① 잠그고 판정 — 캐시가 있으면 끝, 없으면 렌더링 입력을 만든다.
        Prepared prepared = generator.newTransaction().execute(status -> {
            Contract c = access.lock(id);
            var cached = generator.cachedDraft(c);
            if (cached.isPresent()) {
                return new Prepared(storage.download(cached.get()), null);
            }
            // 발송 이후에 다른 원문을 만들면 이미 보낸 파일과 보관본이 달라진다.
            if (c.getStatus() != ContractStatus.REVIEW_PENDING) {
                throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
            }
            return new Prepared(null, generator.prepare(c));
        });
        if (prepared.cached() != null) {
            return prepared.cached();
        }

        // ② 렌더링 — 트랜잭션 밖.
        byte[] bytes = generator.render(prepared.submission());

        // ③ 다시 잠그고 확인 — 그 사이 검토 요청 경로가 먼저 만들었으면 그 파일을 준다.
        return generator.newTransaction().execute(status -> {
            Contract c = access.lock(id);
            var cached = generator.cachedDraft(c);
            if (cached.isPresent()) {
                return storage.download(cached.get());
            }
            // 렌더링하는 사이 취소·재요청·승인됐다 — 방금 만든 파일은 지금 계약과 다르다. 다시 받으면 새로 만든다.
            if (!generator.isCurrent(c, prepared.submission())) {
                throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
            }
            return storage.download(generator.store(c, bytes, ContractActorType.ADMIN, operator, actor));
        });
    }

    private record Prepared(DownloadResponse cached, Submission submission) {
    }
}
