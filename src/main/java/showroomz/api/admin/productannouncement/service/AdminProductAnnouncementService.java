package showroomz.api.admin.productannouncement.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import showroomz.api.admin.productannouncement.dto.*;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.productannouncement.entity.ProductAnnouncement;
import showroomz.domain.productannouncement.repository.ProductAnnouncementRepository;
import showroomz.domain.productannouncement.type.ExposureType;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminProductAnnouncementService {

    private final ProductAnnouncementRepository productAnnouncementRepository;
    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<AdminProductAnnouncementListItem> search(
            String keyword,
            String category,
            ProductAnnouncementDisplayStatus displayStatus,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            PagingRequest pagingRequest
    ) {
        Pageable pageable = pagingRequest.toPageable();
        Page<ProductAnnouncement> page = productAnnouncementRepository.search(
                pageable,
                keyword,
                category,
                displayStatus,
                createdFrom,
                createdTo
        );
        List<AdminProductAnnouncementListItem> content = page.getContent().stream()
                .map(AdminProductAnnouncementListItem::from)
                .toList();
        return new PageResponse<>(content, page);
    }

    @Transactional
    public Long create(AdminProductAnnouncementCreateRequest request) {
        validateDisplayPeriod(request.getDisplayPeriodSet(), request.getDisplayStartDate(), request.getDisplayEndDate());
        validateTargets(request.getExposureType(), request.getTargetProductIds());

        ProductAnnouncement announcement = ProductAnnouncement.builder()
                .category(request.getCategory().trim())
                .title(request.getTitle().trim())
                .content(request.getContent())
                .exposureType(request.getExposureType())
                .displayPeriodSet(Boolean.TRUE.equals(request.getDisplayPeriodSet()))
                .displayStartDate(request.getDisplayStartDate())
                .displayEndDate(request.getDisplayEndDate())
                .popup(Boolean.TRUE.equals(request.getPopup()))
                .displayStatus(request.getDisplayStatus())
                .build();

        ProductAnnouncement saved = productAnnouncementRepository.save(announcement);
        applyTargets(saved, request.getExposureType(), request.getTargetProductIds());
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public AdminProductAnnouncementDetailResponse getDetail(Long id) {
        ProductAnnouncement announcement = productAnnouncementRepository.findByIdWithTargetsAndProducts(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND));
        return AdminProductAnnouncementDetailResponse.from(announcement);
    }

    @Transactional
    public void update(Long id, AdminProductAnnouncementUpdateRequest request) {
        ProductAnnouncement announcement = productAnnouncementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND));

        validateDisplayPeriod(request.getDisplayPeriodSet(), request.getDisplayStartDate(), request.getDisplayEndDate());
        validateTargets(request.getExposureType(), request.getTargetProductIds());

        announcement.update(
                request.getCategory().trim(),
                request.getTitle().trim(),
                request.getContent(),
                request.getExposureType(),
                Boolean.TRUE.equals(request.getDisplayPeriodSet()),
                request.getDisplayStartDate(),
                request.getDisplayEndDate(),
                Boolean.TRUE.equals(request.getPopup()),
                request.getDisplayStatus()
        );
        applyTargets(announcement, request.getExposureType(), request.getTargetProductIds());
    }

    @Transactional
    public void delete(Long id) {
        if (!productAnnouncementRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND);
        }
        productAnnouncementRepository.deleteById(id);
    }

    @Transactional
    public int bulkDelete(AdminProductAnnouncementBulkDeleteRequest request) {
        List<Long> ids = request.getAnnouncementIds();
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        return productAnnouncementRepository.deleteByIdIn(ids);
    }

    @Transactional
    public int bulkUpdateStatus(AdminProductAnnouncementBulkStatusRequest request) {
        List<Long> ids = request.getAnnouncementIds();
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        ProductAnnouncementDisplayStatus status = request.getDisplayStatus();
        int updated = productAnnouncementRepository.updateDisplayStatusByIdIn(ids, status);
        entityManager.flush();
        entityManager.clear();
        return updated;
    }

    private void validateDisplayPeriod(boolean displayPeriodSet, LocalDateTime start, LocalDateTime end) {
        if (!displayPeriodSet) {
            return;
        }
        if (start == null || end == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "노출 기간을 사용하는 경우 시작/종료 일시를 모두 입력해야 합니다.");
        }
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "노출 시작 일시는 종료 일시보다 이전이어야 합니다.");
        }
    }

    private void validateTargets(ExposureType exposureType, List<Long> targetProductIds) {
        if (exposureType == ExposureType.ALL) {
            if (!CollectionUtils.isEmpty(targetProductIds)) {
                throw new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_TARGET_NOT_ALLOWED_FOR_ALL);
            }
            return;
        }
        if (CollectionUtils.isEmpty(targetProductIds)) {
            throw new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_TARGET_REQUIRED);
        }
        Set<Long> unique = new HashSet<>(targetProductIds);
        if (unique.size() != targetProductIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대상 상품 ID에 중복이 있습니다.");
        }
    }

    private void applyTargets(ProductAnnouncement announcement, ExposureType exposureType, List<Long> targetProductIds) {
        if (exposureType == ExposureType.ALL) {
            announcement.replaceTargetsFromProducts(List.of());
            return;
        }
        List<Product> products = productRepository.findByProductIdIn(targetProductIds);
        if (products.size() != targetProductIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND_FOR_ANNOUNCEMENT);
        }
        announcement.replaceTargetsFromProducts(products);
    }
}
