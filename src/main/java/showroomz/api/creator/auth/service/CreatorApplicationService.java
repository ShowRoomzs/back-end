package showroomz.api.creator.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationResponse;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.auth.DTO.CreatorApplicationRequest;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorApplicationService {

    private final CreatorApplicationRepository creatorApplicationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Transactional
    public void apply(Long userId, CreatorApplicationRequest request) {
        validateRequiredAgreements(request);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRoleType() == RoleType.CREATOR) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        if (creatorApplicationRepository.existsByUser_IdAndStatus(userId, CreatorApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_APPLICATION);
        }

        CreatorApplication application = CreatorApplication.createApplication(
                user,
                request.getSnsType(),
                request.getChannelUrl(),
                request.getFollowerCount(),
                request.getBusinessEmail()
        );

        user.setServiceAgree(Boolean.TRUE.equals(request.getAgreeTermsOfService()));
        user.setPrivacyAgree(Boolean.TRUE.equals(request.getAgreePrivacyPolicy()));
        user.setMarketingAgree(Boolean.TRUE.equals(request.getAgreeMarketingPolicy()));

        creatorApplicationRepository.save(application);
    }

    public Page<CreatorApplicationResponse> getApplications(Pageable pageable) {
        return creatorApplicationRepository.findAllWithUser(pageable)
                .map(CreatorApplicationResponse::new);
    }

    @Transactional
    public void approve(Long applicationId) {
        CreatorApplication application = creatorApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != CreatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        application.approve();

        Users user = application.getUser();
        user.updateRoleType(RoleType.CREATOR);

        mailService.sendCreatorApprovalEmail(
                user.getEmail(),
                user.getNickname(),
                application.getProcessedAt()
        );
    }

    @Transactional
    public void reject(Long applicationId, CreatorApplicationRejectRequest request) {
        CreatorApplication application = creatorApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != CreatorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATUS);
        }

        String reasonSummary = request.getRejectReasonType().getDescription();
        String reasonDetail = request.getRejectReasonDetail();

        String fullRejectReason = reasonSummary;
        if (reasonDetail != null && !reasonDetail.isBlank()) {
            fullRejectReason += " - " + reasonDetail;
        }

        application.reject(fullRejectReason);

        Users user = application.getUser();
        mailService.sendCreatorRejectionEmail(
                user.getEmail(),
                user.getNickname(),
                application.getProcessedAt(),
                reasonSummary,
                reasonDetail
        );
    }

    private void validateRequiredAgreements(CreatorApplicationRequest request) {
        if (!Boolean.TRUE.equals(request.getAgreeTermsOfService())
                || !Boolean.TRUE.equals(request.getAgreeOperationalPolicy())
                || !Boolean.TRUE.equals(request.getAgreePrivacyPolicy())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
