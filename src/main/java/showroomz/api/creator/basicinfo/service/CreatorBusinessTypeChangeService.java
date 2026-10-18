package showroomz.api.creator.basicinfo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.basicinfo.dto.CreatorBasicInfoDto;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.service.ChangeRequestCodeGenerator;
import showroomz.domain.changerequest.service.ChangeRequestFieldResolver;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.ResidentRegistrationNumber;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.PersonalDataCipher;

/**
 * 스튜디오 사업자 구분 변경 요청(T4 ~ T10 · 50 설계서 7절) — 어드민 변경 요청 큐(§16-2a)에 인플루언서 행을 넣는 생산자다. 처리(승인 · 반려)는
 * 어드민이 한다. 계좌 변경 중 · 계좌 확인 실패 · 정지 · 탈퇴 심사 중에도 요청할 수 있다(§50-2 「버튼은 모든 상태에서 같은 자리」).
 * 주민번호 앞 6자리와 본인인증 생년월일은 여기서 대조하지 않는다 — 운영자가 서류로 한다(어드민 상세가 계산해 보여 준다).
 */
@Service
@RequiredArgsConstructor
public class CreatorBusinessTypeChangeService {

    private static final String BUSINESS_REGISTRATION_NUMBER_PATTERN = "^\\d{3}-\\d{2}-\\d{5}$";
    /** 스튜디오 서류 상한(T3 · 온보딩 rev.3) — 업로드에서 이미 막지만 서버가 한 번 더 본다. */
    private static final long EVIDENCE_MAX_BYTES = 10L * 1024 * 1024;

    private final CreatorRepository creatorRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestCodeGenerator codeGenerator;
    private final PersonalDataCipher cipher;

    @Transactional
    public CreatorBasicInfoDto.BusinessTypeChangeCreated create(Long userId, CreatorBasicInfoDto.BusinessTypeChangeRequest body) {
        Creator creator = lockedCreatorOf(userId);
        CreatorBusinessType target = body.getTargetType();
        if (target == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "바꿀 사업자 구분을 선택해 주세요.");
        }
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        // 두 탭에서 동시에 눌러도 엉뚱한 방향의 요청이 생기지 않게 — 방향은 현재 구분의 반대만.
        if (target == creator.getBusinessType()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH);
        }
        String businessRegistrationNumber = null;
        String residentNumber = null;
        if (target == CreatorBusinessType.BUSINESS) {
            businessRegistrationNumber = body.getBusinessRegistrationNumber() == null ? null : body.getBusinessRegistrationNumber().strip();
            if (businessRegistrationNumber == null || !businessRegistrationNumber.matches(BUSINESS_REGISTRATION_NUMBER_PATTERN)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)");
            }
        } else {
            residentNumber = ResidentRegistrationNumber.normalize(body.getResidentRegistrationNumber());
            if (residentNumber == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호를 정확히 입력해 주세요.");
            }
        }
        if (isBlank(body.getEvidenceFileUrl()) || isBlank(body.getEvidenceFileName()) || body.getEvidenceFileSize() == null) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_EVIDENCE_REQUIRED);
        }
        if (body.getEvidenceFileSize() > EVIDENCE_MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "파일 크기가 10MB를 초과했습니다. 10MB 이하의 파일을 선택해 주세요.");
        }
        if (changeRequestRepository.findOpenForCreator(creator.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }

        ChangeRequestType type = ChangeRequestType.toward(target);
        ChangeRequest request = ChangeRequest.createForCreator(codeGenerator.next(), creator, type,
                body.getEvidenceFileUrl(), body.getEvidenceFileName(), body.getEvidenceFileSize());
        // 현재 값은 서버 스냅샷 · 사업자 구분은 코드로 저장한다(라벨은 조회 시).
        request.addItem(ChangeRequestField.CREATOR_BUSINESS_TYPE,
                ChangeRequestFieldResolver.currentValue(ChangeRequestField.CREATOR_BUSINESS_TYPE, creator), target.name(), 0);
        if (type == ChangeRequestType.TO_BUSINESS) {
            request.addItem(ChangeRequestField.CREATOR_BUSINESS_REG_NUMBER,
                    ChangeRequestFieldResolver.currentValue(ChangeRequestField.CREATOR_BUSINESS_REG_NUMBER, creator),
                    businessRegistrationNumber, 1);
        } else {
            request.addItem(ChangeRequestField.CREATOR_RESIDENT_NUMBER,
                    ChangeRequestFieldResolver.currentValue(ChangeRequestField.CREATOR_RESIDENT_NUMBER, creator),
                    ResidentRegistrationNumber.mask(residentNumber), 1);
            request.attachResidentNumber(cipher.encrypt(residentNumber));
        }

        // 새 요청이 닫지 않은 옛 결과 배너(승인 · 반려)를 대신한다 — 새로 냈다는 것 자체가 읽었다는 뜻이다.
        changeRequestRepository.findProcessedForCreator(creator.getId()).stream()
                .filter(r -> r.getResultAcknowledgedAt() == null)
                .forEach(ChangeRequest::acknowledge);

        try {
            changeRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }
        return CreatorBasicInfoDto.BusinessTypeChangeCreated.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType().name())
                .status(request.getStatus().name())
                .requestedAt(request.getRequestedAt())
                .build();
    }

    /** 확인 대기 중 직접 취소(T4 · T9) — 정산은 기존 구분으로 계속되므로 아무 영향이 없다. 주민번호 암호문은 즉시 비워진다. */
    @Transactional
    public void cancel(Long userId, Long requestId) {
        ChangeRequest request = ownedRequest(userId, requestId);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }
        request.cancel();
    }

    /** 결과 배너 닫기(T4a · T5 · T9a · T10) — 브랜드 확인과 같은 규칙. */
    @Transactional
    public void acknowledge(Long userId, Long requestId) {
        ChangeRequest request = ownedRequest(userId, requestId);
        if (request.getStatus() != ChangeRequestStatus.APPROVED && request.getStatus() != ChangeRequestStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "처리가 끝난 요청만 확인할 수 있습니다.");
        }
        if (request.getResultAcknowledgedAt() == null) {
            request.acknowledge();
        }
    }

    private ChangeRequest ownedRequest(Long userId, Long requestId) {
        Creator creator = creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        ChangeRequest request = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_FOUND));
        if (request.getCreator() == null || !request.getCreator().getId().equals(creator.getId())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ACCESS_DENIED);
        }
        return request;
    }

    private Creator lockedCreatorOf(Long userId) {
        Long creatorId = creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND)).getId();
        return creatorRepository.findForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
