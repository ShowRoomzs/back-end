package showroomz.api.seller.productannouncement.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkDeleteRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkStatusRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementCreateRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementDetailResponse;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementListItem;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementUpdateRequest;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerProductAnnouncementService {

    private final ProductAnnouncementRepository productAnnouncementRepository;
    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<SellerProductAnnouncementListItem> search(
            String sellerEmail,
            String keyword,
            String category,
            ProductAnnouncementDisplayStatus displayStatus,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            PagingRequest pagingRequest
    ) {
        Long marketId = resolveMarketId(sellerEmail);
        Pageable pageable = pagingRequest.toPageable();
        Page<ProductAnnouncement> page = productAnnouncementRepository.search(
                marketId,
                pageable,
                keyword,
                category,
                displayStatus,
                createdFrom,
                createdTo
        );
        List<SellerProductAnnouncementListItem> content = page.getContent().stream()
                .map(SellerProductAnnouncementListItem::from)
                .toList();
        return new PageResponse<>(content, page);
    }

    @Transactional
    public Long create(String sellerEmail, SellerProductAnnouncementCreateRequest request) {
        Seller seller = resolveSeller(sellerEmail);
        Market market = marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));

        validateDisplayPeriod(request.getDisplayPeriodSet(), request.getDisplayStartDate(), request.getDisplayEndDate());
        validateTargets(request.getExposureType(), request.getTargetProductIds());

        ProductAnnouncement announcement = ProductAnnouncement.builder()
                .market(market)
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
        applyTargets(saved, request.getExposureType(), request.getTargetProductIds(), seller.getId());
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public SellerProductAnnouncementDetailResponse getDetail(String sellerEmail, Long id) {
        Long marketId = resolveMarketId(sellerEmail);
        return productAnnouncementRepository.findByIdWithTargetsAndProductsAndMarketId(id, marketId)
                .map(SellerProductAnnouncementDetailResponse::from)
                .orElseThrow(() -> detailAccessDeniedOrNotFound(id, marketId));
    }

    @Transactional
    public void update(String sellerEmail, Long id, SellerProductAnnouncementUpdateRequest request) {
        Seller seller = resolveSeller(sellerEmail);
        Long marketId = resolveMarketId(sellerEmail);

        ProductAnnouncement announcement = productAnnouncementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND));
        assertMarketOwnership(announcement, marketId);

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
        applyTargets(announcement, request.getExposureType(), request.getTargetProductIds(), seller.getId());
    }

    @Transactional
    public void delete(String sellerEmail, Long id) {
        Long marketId = resolveMarketId(sellerEmail);
        ProductAnnouncement announcement = productAnnouncementRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND));
        assertMarketOwnership(announcement, marketId);
        productAnnouncementRepository.deleteById(id);
    }

    @Transactional
    public int bulkDelete(String sellerEmail, SellerProductAnnouncementBulkDeleteRequest request) {
        List<Long> ids = request.getAnnouncementIds();
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        Long marketId = resolveMarketId(sellerEmail);
        verifyAnnouncementIdsForMarket(ids, marketId);
        return productAnnouncementRepository.deleteByIdInAndMarketId(ids, marketId);
    }

    @Transactional
    public int bulkUpdateStatus(String sellerEmail, SellerProductAnnouncementBulkStatusRequest request) {
        List<Long> ids = request.getAnnouncementIds();
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        Long marketId = resolveMarketId(sellerEmail);
        verifyAnnouncementIdsForMarket(ids, marketId);
        ProductAnnouncementDisplayStatus status = request.getDisplayStatus();
        int updated = productAnnouncementRepository.updateDisplayStatusByIdInAndMarketId(ids, status, marketId);
        entityManager.flush();
        entityManager.clear();
        return updated;
    }

    private RuntimeException detailAccessDeniedOrNotFound(Long id, Long marketId) {
        ProductAnnouncement announcement = productAnnouncementRepository.findById(id).orElse(null);
        if (announcement == null) {
            return new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND);
        }
        if (!announcement.getMarket().getId().equals(marketId)) {
            return new AccessDeniedException("해당 상품 공지에 접근할 권한이 없습니다.");
        }
        return new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND);
    }

    private void assertMarketOwnership(ProductAnnouncement announcement, Long marketId) {
        if (!announcement.getMarket().getId().equals(marketId)) {
            throw new AccessDeniedException("해당 상품 공지에 접근할 권한이 없습니다.");
        }
    }

    private void verifyAnnouncementIdsForMarket(List<Long> ids, Long marketId) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<ProductAnnouncement> owned = productAnnouncementRepository.findAllByIdInAndMarket_Id(distinct, marketId);
        if (owned.size() == distinct.size()) {
            return;
        }
        Set<Long> foundIds = owned.stream().map(ProductAnnouncement::getId).collect(Collectors.toSet());
        for (Long id : distinct) {
            if (foundIds.contains(id)) {
                continue;
            }
            ProductAnnouncement a = productAnnouncementRepository.findById(id).orElseThrow(
                    () -> new BusinessException(ErrorCode.PRODUCT_ANNOUNCEMENT_NOT_FOUND));
            if (!a.getMarket().getId().equals(marketId)) {
                throw new AccessDeniedException("해당 상품 공지에 접근할 권한이 없습니다.");
            }
        }
    }

    private Seller resolveSeller(String sellerEmail) {
        return sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_INFO));
    }

    private Long resolveMarketId(String sellerEmail) {
        Seller seller = resolveSeller(sellerEmail);
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA))
                .getId();
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

    private void applyTargets(ProductAnnouncement announcement, ExposureType exposureType, List<Long> targetProductIds, Long sellerId) {
        if (exposureType == ExposureType.ALL) {
            announcement.replaceTargetsFromProducts(List.of());
            return;
        }
        List<Product> products = productRepository.findAllByProductIdsAndSellerId(targetProductIds, sellerId);
        if (products.size() != targetProductIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND_FOR_ANNOUNCEMENT);
        }
        announcement.replaceTargetsFromProducts(products);
    }
}
