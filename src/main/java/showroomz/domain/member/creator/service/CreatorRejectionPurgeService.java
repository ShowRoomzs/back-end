package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.global.utils.BusinessRegistrationNumberHasher;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인플루언서 입점 반려 정보 파기(수정요청 B-8 · 확정 정책) — 반려 처리 + 14일 보관 뒤. 실명 · 생년월일 · 팔로워 수 · 업무 이메일을
 * 지우고 연락처는 일방향 해시만 남긴다(해시 방식은 법-43 회신 후 결정 · B-9 보류 — 지금 방식 그대로). 신청 때 받은 회원의 약관 동의도
 * 지운다 — 다만 그 뒤에 같은 회원이 새로 신청했으면 동의는 새 신청의 것이라 두고, 이미 인플루언서가 된 회원도 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CreatorRejectionPurgeService {

    private final CreatorApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public List<Long> findIdsToPurge(LocalDateTime now, int limit) {
        return applicationRepository.findIdsToPurgeRejected(now, PageRequest.of(0, limit));
    }

    @Transactional
    public boolean purge(Long applicationId, LocalDateTime now) {
        CreatorApplication application = applicationRepository.findByIdWithUser(applicationId).orElse(null);
        if (application == null || application.getStatus() != CreatorApplicationStatus.REJECTED
                || application.isPersonalDataPurged() || application.getPersonalDataPurgeDueAt() == null
                || application.getPersonalDataPurgeDueAt().isAfter(now)) {
            return false;
        }
        var user = application.getUser();
        if (!applicationRepository.existsNewerApplication(user.getId(), application.getId())) {
            user.purgeAgreementsOnCreatorRejection();
        }
        application.purgePersonalData(BusinessRegistrationNumberHasher.hash(application.getPhoneNumber()), now);
        return true;
    }
}
