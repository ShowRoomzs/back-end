package showroomz.api.seller.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.seller.order.dto.BatchActionResponse;
import showroomz.api.seller.order.dto.CancelRequestRejectRequest;
import showroomz.api.seller.order.dto.PrepareStartRequest;
import showroomz.api.seller.order.dto.PurchaseOrderRequest;
import showroomz.api.seller.order.dto.PurchaseOrderTemplateDto;
import showroomz.api.seller.order.dto.SellerDirectCancelRequest;
import showroomz.api.seller.order.dto.SellerOrderDetailResponse;
import showroomz.api.seller.order.dto.ShipmentParseResponse;
import showroomz.api.seller.order.dto.ShipmentRegisterRequest;
import showroomz.api.seller.order.dto.ShipmentUpdateRequest;
import showroomz.api.seller.order.service.SellerOrderAccessGuard.SellerScope;
import showroomz.domain.order.service.OrderCancelRequestService;
import showroomz.domain.order.entity.MarketPurchaseOrderTemplate;
import showroomz.domain.order.entity.OrderCancelRequest;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.entity.PurchaseOrderDownloadLog;
import showroomz.domain.order.repository.MarketPurchaseOrderTemplateRepository;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.PurchaseOrderDownloadLogRepository;
import showroomz.domain.order.repository.SellerOrderRow;
import showroomz.domain.order.repository.SellerOrderSearchCondition;
import showroomz.domain.order.service.OrderFulfillmentService;
import showroomz.domain.order.type.CancelRequestStatus;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.FulfillmentActorType;
import showroomz.domain.order.type.FulfillmentEventType;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderCancelType;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderTab;
import showroomz.domain.order.type.PurchaseOrderColumn;
import showroomz.domain.order.type.RefundTaskSource;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.delivery.tracker.DeliveryTrackerPort;
import showroomz.global.delivery.tracker.DeliveryTrackerPort.ValidationResult;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 파트너센터 주문 실행(34 설계서 3절) — 준비 시작 · 발주서 · 송장 · 취소 3경로의 브랜드 쪽.
 *
 * <p>다건 액션은 행 단위 부분 성공이다 — 조건부 UPDATE 0행으로 떨어진 행만 사유와 함께 제외하고 나머지는 진행한다.
 * 돈의 시점·방향을 바꾸는 전이(배송완료·구매확정·환불 집행)는 여기에 없다(설계서 0-3).
 */
@Service
@RequiredArgsConstructor
public class SellerOrderCommandService {

    private static final Pattern SUB_ORDER_PATTERN = Pattern.compile(".+-\\d{2}$");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final SellerOrderAccessGuard accessGuard;
    private final SellerOrderQueryService queryService;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderCancelRequestService cancelRequestService;
    private final MarketPurchaseOrderTemplateRepository templateRepository;
    private final PurchaseOrderDownloadLogRepository downloadLogRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final PurchaseOrderExcelWriter excelWriter;
    private final ShipmentExcelParser excelParser;
    private final DeliveryTrackerPort tracker;
    private final OrderProperties orderProperties;

    // ------------------------------------------------------------------ 준비 시작(3-1)

    @Transactional
    public BatchActionResponse prepareStart(String sellerEmail, PrepareStartRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<BatchActionResponse.Skipped> skipped = new ArrayList<>();
        int succeeded = 0;
        LinkedHashSet<Long> ids = new LinkedHashSet<>(request.deliveryGroupIds());
        // 준비 시작 사유를 구분해 남긴다 — 개별 / 일괄 / 발주서 다운로드(어드민 06a 이력 · 1009 기획 수정본).
        String reason = ids.size() > 1 ? "일괄 준비 시작" : "개별 준비 시작";
        for (Long id : ids) {
            if (deliveryGroupRepository.startPreparation(id, scope.market().getId(), scope.sellerId(), now) == 1) {
                fulfillmentService.appendHistory(id, FulfillmentEventType.PREPARE_STARTED, FulfillmentActorType.SELLER,
                        scope.sellerId(), reason, now);
                succeeded++;
            } else {
                skipped.add(skipReason(id, scope));
            }
        }
        return new BatchActionResponse(succeeded, skipped);
    }

    // ------------------------------------------------------------------ 발주서(3-1)

    public record PurchaseOrderFile(byte[] content, String filename, int deliveryGroupCount, int preparedCount) {
    }

    @Transactional
    public PurchaseOrderFile downloadPurchaseOrder(String sellerEmail, PurchaseOrderRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrderColumn> columns = new ArrayList<>(new LinkedHashSet<>(request.columns()));
        if (columns.isEmpty()) {
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_COLUMNS_REQUIRED);
        }
        if (request.downloadOnlyRequested()) {
            // 「다운로드만」은 없어졌다(34 설계서 3-1). 조용히 무시하면 그 옵션을 고른 브랜드의 주문이 준비 시작되고
            // 소비자 취소권이 닫힌다 — 되돌릴 수 없으므로 거절한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "발주서를 내려받으면 준비 시작으로 처리됩니다. 다운로드만 하는 옵션은 지원하지 않습니다.");
        }

        List<SellerOrderRow> rows = collectTargets(scope, request, now);
        if (rows.size() > orderProperties.getPurchaseOrderMaxGroups()) {
            // 조용히 잘라 내려보내면 브랜드는 전부 받은 줄 안다 — 나눠 받게 한다.
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_TOO_MANY);
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_EMPTY);
        }
        // 검토 중 취소 요청이 걸린 하위주문은 작업 큐 밖이다 — 발주서에도 싣지 않는다.
        Set<Long> pendingIds = cancelRequestRepository.findPendingByDeliveryGroupIds(
                        rows.stream().map(row -> row.group().getId()).toList()).stream()
                .map(r -> r.getDeliveryGroup().getId())
                .collect(Collectors.toSet());
        rows = rows.stream().filter(row -> !pendingIds.contains(row.group().getId())).toList();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_EMPTY);
        }

        // 발주서 = 준비 시작(34 설계서 3-1) — 발주서를 뽑는 것은 보내겠다는 결정이다(§34-4). 신규 행을 하위주문 id
        // 오름차순으로 전이한다 — 소비자 배송지 변경(하위주문 → 주문 순서로 잠근다)과 잠금 순서가 같아 교착이 없다.
        int prepared = 0;
        List<SellerOrderRow> newRows = rows.stream()
                .filter(row -> row.group().getFulfillmentStatus() == FulfillmentStatus.NEW)
                .sorted(Comparator.comparing(row -> row.group().getId()))
                .toList();
        for (SellerOrderRow row : newRows) {
            if (deliveryGroupRepository.startPreparation(row.group().getId(), scope.market().getId(),
                    scope.sellerId(), now) == 1) {
                fulfillmentService.appendHistory(row.group().getId(), FulfillmentEventType.PREPARE_STARTED,
                        FulfillmentActorType.SELLER, scope.sellerId(), "발주서 다운로드", now);
                prepared++;
            }
        }

        // 전이 뒤에 하위주문·주문을 잠금 읽기로 다시 읽는다 — 앞에서 읽은 엔티티는 전이 UPDATE 가 영속성 컨텍스트를
        // 비워 분리됐고, 일반 SELECT 는 그사이 커밋된 배송지 변경을 보지 못한다(REPEATABLE READ 스냅숏).
        // 상품준비중이 아닌 행(결제 취소 선점 · 그사이 도착한 취소 요청 · 상태 변경으로 전이 0행)은 파일에서도 뺀다 —
        // 발주서에 실린 주문은 배송지가 더는 바뀌지 않아야 한다.
        Map<Long, OrderDeliveryGroup> preparing = deliveryGroupRepository.findAllWithOrderForShare(
                        rows.stream().map(row -> row.group().getId()).toList()).stream()
                .filter(group -> group.getFulfillmentStatus() == FulfillmentStatus.PREPARING)
                .collect(Collectors.toMap(OrderDeliveryGroup::getId, group -> group));
        rows = rows.stream().filter(row -> preparing.containsKey(row.group().getId())).toList();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_EMPTY);
        }

        Map<Long, List<OrderProduct>> itemsByGroup = orderProductRepository.findByDeliveryGroupIds(
                        rows.stream().map(row -> row.group().getId()).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));

        List<PurchaseOrderExcelWriter.Line> lines = new ArrayList<>();
        for (SellerOrderRow row : rows) {
            OrderDeliveryGroup group = preparing.get(row.group().getId());
            var order = group.getOrder();
            String address = (order.getAddress() == null ? "" : order.getAddress())
                    + (order.getDetailAddress() == null ? "" : " " + order.getDetailAddress());
            for (OrderProduct item : itemsByGroup.getOrDefault(group.getId(), List.of())) {
                if (item.getStatus() == OrderProductStatus.CANCELLED) {
                    continue; // 취소 항목은 발송하지 않는다
                }
                lines.add(new PurchaseOrderExcelWriter.Line(
                        row.orderNumber(), group.getSubOrderNumber(), order.getRecipientName(),
                        order.getRecipientPhone(), order.getZipCode(), address.trim(),
                        item.getProductName(), item.getOptionName(), item.getQuantity(),
                        order.getDeliveryMemo(), row.groupBuyTitle(), row.paidAt(),
                        item.getPrice() * item.getQuantity()));
            }
        }

        if (Boolean.TRUE.equals(request.saveAsDefault())) {
            upsertTemplate(scope, columns);
        }
        String columnCsv = columns.stream().map(Enum::name).collect(Collectors.joining(","));
        // 반출 기록은 다운로드와 같은 트랜잭션 — 개인정보가 나가는데 기록이 빠지면 안 된다(§34-11).
        downloadLogRepository.save(PurchaseOrderDownloadLog.builder()
                .marketId(scope.market().getId())
                .sellerId(scope.sellerId())
                .deliveryGroupCount(rows.size())
                .columns(columnCsv)
                .prepareStarted(true)
                .downloadedAt(now)
                .build());

        byte[] content = excelWriter.write(columns, lines);
        return new PurchaseOrderFile(content, "발주서_" + FILE_STAMP.format(now) + ".xlsx", rows.size(), prepared);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderTemplateDto.Response getTemplate(String sellerEmail) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        List<PurchaseOrderColumn> columns = templateRepository.findByMarket_IdAndTemplateType(
                        scope.market().getId(), MarketPurchaseOrderTemplate.TYPE_PURCHASE_ORDER)
                .map(MarketPurchaseOrderTemplate::columnList)
                .orElseGet(() -> Arrays.stream(PurchaseOrderColumn.values())
                        .filter(PurchaseOrderColumn::isBasic).toList());
        List<PurchaseOrderTemplateDto.Response.Available> available = Arrays.stream(PurchaseOrderColumn.values())
                .map(code -> new PurchaseOrderTemplateDto.Response.Available(code, code.getHeader(), code.isBasic()))
                .toList();
        return new PurchaseOrderTemplateDto.Response(columns, available);
    }

    @Transactional
    public PurchaseOrderTemplateDto.Response updateTemplate(String sellerEmail,
                                                            PurchaseOrderTemplateDto.UpdateRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        List<PurchaseOrderColumn> columns = new ArrayList<>(new LinkedHashSet<>(request.columns()));
        if (columns.isEmpty()) {
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_COLUMNS_REQUIRED);
        }
        upsertTemplate(scope, columns);
        return getTemplate(sellerEmail);
    }

    // ------------------------------------------------------------------ 송장 등록(3-2)

    @Transactional
    public BatchActionResponse registerShipments(String sellerEmail, ShipmentRegisterRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<BatchActionResponse.Skipped> skipped = new ArrayList<>();
        Map<String, Long> seenInvoices = new HashMap<>();
        int succeeded = 0;

        for (ShipmentRegisterRequest.Row row : request.rows()) {
            String trackingNumber = normalize(row.trackingNumber());
            if (trackingNumber.isEmpty()) {
                continue; // 빈 값은 조용히 확정 대상에서만 제외한다(§34-5 전역 규칙)
            }
            String invoiceKey = row.carrier().name() + ":" + trackingNumber;
            Long first = seenInvoices.putIfAbsent(invoiceKey, row.deliveryGroupId());
            if (first != null && !first.equals(row.deliveryGroupId())) {
                skipped.add(new BatchActionResponse.Skipped(row.deliveryGroupId(), "INVOICE_DUPLICATE",
                        "같은 요청 안에서 중복된 송장번호입니다."));
                continue;
            }
            // ③ 전역 중복 — 겹치는 주문을 지목한다(지목하지 않으면 어느 쪽을 고칠지 모른다 · §34-5).
            OrderDeliveryGroup duplicate = findDuplicate(row.carrier(), trackingNumber, row.deliveryGroupId());
            if (duplicate != null) {
                skipped.add(new BatchActionResponse.Skipped(row.deliveryGroupId(), "INVOICE_DUPLICATE",
                        duplicateMessage(duplicate, scope)));
                continue;
            }
            // 추적 연동 업체만 — 출고 · 회수 · 재발송이 같은 목록을 쓴다(거래 관리 결정 5).
            if (!row.carrier().isSelectable()) {
                skipped.add(new BatchActionResponse.Skipped(row.deliveryGroupId(), "CARRIER_INVALID",
                        "추적 연동 택배사만 선택할 수 있습니다."));
                continue;
            }
            // ③ 형식 — 연동 업체가 최신 규칙으로 판정한다. 자릿수인지 체크디지트인지 구분하지 않는다(§34-5).
            ValidationResult validation = tracker.validateInvoice(row.carrier(), trackingNumber);
            if (validation == ValidationResult.INVALID) {
                skipped.add(new BatchActionResponse.Skipped(row.deliveryGroupId(), "INVOICE_FORMAT_INVALID",
                        "송장번호 형식이 올바르지 않습니다. 다시 확인해 주세요."));
                continue;
            }
            if (deliveryGroupRepository.registerInvoice(row.deliveryGroupId(), scope.market().getId(), row.carrier(),
                    trackingNumber, now) == 1) {
                String detail = row.carrier().getLabel() + " " + trackingNumber
                        + (validation == ValidationResult.UNAVAILABLE ? " · 형식 검증 생략(연동 전)" : "");
                fulfillmentService.appendHistory(row.deliveryGroupId(), FulfillmentEventType.INVOICE_REGISTERED,
                        FulfillmentActorType.SELLER, scope.sellerId(), detail, now);
                succeeded++;
            } else {
                skipped.add(skipReason(row.deliveryGroupId(), scope));
            }
        }
        return new BatchActionResponse(succeeded, skipped);
    }

    /** 업로드 파싱·분류(E3) — <b>상태를 바꾸지 않는다.</b> 확정은 수기 입력과 같은 {@code registerShipments}다. */
    @Transactional(readOnly = true)
    public ShipmentParseResponse parseShipments(String sellerEmail, MultipartFile file) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        List<ShipmentExcelParser.RawRow> rawRows;
        try {
            rawRows = excelParser.parse(file.getInputStream(), orderProperties.getShipmentUploadMaxRows());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SHIPMENT_FILE_INVALID);
        }

        Map<String, List<OrderDeliveryGroup>> byOrderNumber = groupByOrderNumber(scope, rawRows);
        Map<String, OrderDeliveryGroup> bySubOrderNumber = groupBySubOrderNumber(scope, rawRows);
        Map<String, OrderDeliveryGroup> activeInvoices = activeInvoiceOwners(rawRows);
        Set<Long> pendingCancelIds = pendingCancelIds(byOrderNumber, bySubOrderNumber);

        Set<String> seenInFile = new HashSet<>();
        Set<Long> seenGroups = new HashSet<>();
        List<ShipmentParseResponse.Row> rows = new ArrayList<>();
        int valid = 0;
        for (ShipmentExcelParser.RawRow raw : rawRows) {
            ShipmentParseResponse.Row parsed = classify(scope, raw, byOrderNumber, bySubOrderNumber, activeInvoices,
                    pendingCancelIds, seenInFile, seenGroups);
            if (parsed.valid()) {
                valid++;
            }
            rows.add(parsed);
        }
        return new ShipmentParseResponse(rows.size(), valid, rows);
    }

    public byte[] shipmentTemplate() {
        return excelParser.template();
    }

    /** 송장 수정(§34-6) — 배송중만 · 수정 이력 기록. {@code shipped_at}은 유지된다(발송기한 판정값). */
    @Transactional
    public SellerOrderDetailResponse updateShipment(String sellerEmail, Long deliveryGroupId,
                                                    ShipmentUpdateRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        OrderDeliveryGroup group = accessGuard.loadOwned(deliveryGroupId, scope);
        LocalDateTime now = LocalDateTime.now();
        String trackingNumber = normalize(request.trackingNumber());
        if (!request.carrier().isSelectable()) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID, "추적 연동 택배사만 선택할 수 있습니다.");
        }
        if (trackingNumber.isEmpty()
                || tracker.validateInvoice(request.carrier(), trackingNumber) == ValidationResult.INVALID) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        if (findDuplicate(request.carrier(), trackingNumber, deliveryGroupId) != null) {
            throw new BusinessException(ErrorCode.INVOICE_DUPLICATE);
        }
        String before = (group.getCarrier() == null ? "" : group.getCarrier().getLabel() + " ")
                + (group.getTrackingNumber() == null ? "" : group.getTrackingNumber());
        if (deliveryGroupRepository.updateInvoice(deliveryGroupId, scope.market().getId(), request.carrier(),
                trackingNumber) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_CHANGED);
        }
        fulfillmentService.appendHistory(deliveryGroupId, FulfillmentEventType.INVOICE_UPDATED,
                FulfillmentActorType.SELLER, scope.sellerId(),
                before.trim() + " → " + request.carrier().getLabel() + " " + trackingNumber, now);
        return queryService.getOrder(sellerEmail, deliveryGroupId);
    }

    // ------------------------------------------------------------------ 취소(3-5)

    /** 직권 취소(E5) — NEW·PREPARING 허용(설계서 0-7). 걸린 취소 요청은 선처리 요구다. */
    @Transactional
    public BatchActionResponse directCancel(String sellerEmail, SellerDirectCancelRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.deliveryGroupIds()));
        Map<Long, OrderDeliveryGroup> owned = deliveryGroupRepository.findAllOwned(ids, scope.market().getId())
                .stream()
                .filter(group -> group.getOrder().getPaidAt() != null) // 결제 전은 셀러 화면 밖 — 없는 주문과 같다
                .collect(Collectors.toMap(OrderDeliveryGroup::getId, Function.identity()));
        Map<Long, List<OrderProduct>> itemsByGroup = owned.isEmpty() ? Map.of()
                : orderProductRepository.findByDeliveryGroupIds(owned.keySet()).stream()
                        .collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));

        List<BatchActionResponse.Skipped> skipped = new ArrayList<>();
        int succeeded = 0;
        for (Long id : ids) {
            OrderDeliveryGroup group = owned.get(id);
            if (group == null) {
                skipped.add(new BatchActionResponse.Skipped(id, ErrorCode.ORDER_GROUP_NOT_FOUND.getCode(),
                        "존재하지 않는 주문입니다."));
                continue;
            }
            if (deliveryGroupRepository.cancelDirect(id, scope.market().getId(), request.reasonCode(),
                    request.consumerMessage(), now) != 1) {
                skipped.add(skipReason(id, scope));
                continue;
            }
            List<OrderProduct> paidItems = itemsByGroup.getOrDefault(id, List.of()).stream()
                    .filter(item -> item.getStatus() == OrderProductStatus.PAID)
                    .toList();
            List<OrderProduct> cancelled = fulfillmentService.cancelItemsWithRestock(paidItems,
                    OrderCancelType.SELLER_DIRECT, now);
            int refundAmount = cancelled.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum()
                    + group.getDeliveryFee(); // 전체 취소 — 전액 환불(배송비 포함 · §34-8)
            fulfillmentService.enqueueRefund(group, RefundTaskSource.SELLER_DIRECT_CANCEL, null, refundAmount);
            fulfillmentService.appendHistory(id, FulfillmentEventType.CANCELLED_BY_SELLER, FulfillmentActorType.SELLER,
                    scope.sellerId(), request.reasonCode().getLabel() + " · " + request.consumerMessage(), now);
            succeeded++;
        }
        return new BatchActionResponse(succeeded, skipped);
    }

    /**
     * 승인(E6) — 요청 항목만 취소 · 남은 항목 발송 · 환불은 PG 즉시 자동 · 단건만(일괄 없음 · §34-8). 응답 기한(1영업일)이 지나면
     * 시스템이 같은 메서드로 자동 승인한다({@code OrderCancelRequestService}).
     */
    @Transactional
    public SellerOrderDetailResponse approveCancelRequest(String sellerEmail, Long cancelRequestId) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        loadOwnedRequest(cancelRequestId, scope);
        Long groupId = cancelRequestService.approve(cancelRequestId, scope.sellerId(), false, LocalDateTime.now());
        return queryService.getOrder(sellerEmail, groupId);
    }

    /** 거부(E7) — 사유(드롭다운) 필수 · 소비자에게 그대로 전달 · 전 항목 배송 진행. 그룹 상태는 바뀐 적이 없어 복귀가 자동이다. */
    @Transactional
    public SellerOrderDetailResponse rejectCancelRequest(String sellerEmail, Long cancelRequestId,
                                                         CancelRequestRejectRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        loadOwnedRequest(cancelRequestId, scope);
        Long groupId = cancelRequestService.reject(cancelRequestId, scope.sellerId(), request.resolvedReasonCode(),
                request.resolvedDetail(), LocalDateTime.now());
        return queryService.getOrder(sellerEmail, groupId);
    }

    // ------------------------------------------------------------------ 내부

    /**
     * 0행의 사유를 가른다 — 취소 요청이 걸렸는지, 그 사이 상태가 변했는지. 요청 여부는 <b>내 마켓의</b> 하위주문일 때만
     * 드러낸다 — 남의 하위주문이면 없는 주문과 같은 「상태 변경」으로 답한다(4-4 존재 비노출).
     */
    private BatchActionResponse.Skipped skipReason(Long deliveryGroupId, SellerScope scope) {
        if (cancelRequestRepository.existsPendingOwned(deliveryGroupId, scope.market().getId())) {
            return new BatchActionResponse.Skipped(deliveryGroupId,
                    ErrorCode.CANCEL_REQUEST_PENDING_EXISTS.getCode(),
                    ErrorCode.CANCEL_REQUEST_PENDING_EXISTS.getMessage());
        }
        return new BatchActionResponse.Skipped(deliveryGroupId, ErrorCode.ORDER_STATE_CHANGED.getCode(),
                ErrorCode.ORDER_STATE_CHANGED.getMessage());
    }

    private OrderCancelRequest loadOwnedRequest(Long cancelRequestId, SellerScope scope) {
        OrderCancelRequest request = cancelRequestRepository.findWithGroup(cancelRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND));
        if (!Objects.equals(request.getDeliveryGroup().getMarketId(), scope.market().getId())) {
            throw new BusinessException(ErrorCode.ORDER_GROUP_NOT_FOUND);
        }
        return request;
    }

    /** 전역 송장 중복(§34-5 ③) — 종결 전 상태에서 같은 (택배사, 번호)를 쓰는 다른 하위주문. */
    private OrderDeliveryGroup findDuplicate(DeliveryCarrier carrier, String trackingNumber, Long selfId) {
        return deliveryGroupRepository.findActiveByInvoice(carrier, trackingNumber,
                        FulfillmentStatus.INVOICE_ACTIVE).stream()
                .filter(g -> !g.getId().equals(selfId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 중복 안내 — 내 주문이면 겹치는 주문번호를 지목하고(어느 쪽을 고칠지 알아야 한다 · §34-5),
     * 다른 브랜드의 주문이면 번호를 숨긴다(4-4 존재 비노출).
     */
    private String duplicateMessage(OrderDeliveryGroup owner, SellerScope scope) {
        return Objects.equals(owner.getMarketId(), scope.market().getId())
                ? owner.getOrder().getOrderNumber() + "에 이미 등록된 번호입니다."
                : "다른 주문에 이미 등록된 번호입니다.";
    }

    private void upsertTemplate(SellerScope scope, List<PurchaseOrderColumn> columns) {
        templateRepository.findByMarket_IdAndTemplateType(scope.market().getId(),
                        MarketPurchaseOrderTemplate.TYPE_PURCHASE_ORDER)
                .ifPresentOrElse(
                        template -> template.update(columns, scope.sellerId()),
                        () -> templateRepository.save(
                                MarketPurchaseOrderTemplate.of(scope.market(), columns, scope.sellerId())));
    }

    /**
     * 발주서 대상 — 선택 건, 선택 없이 열면 현재 탭 전체(목록과 같은 필터 · §34-4). 발주서는 신규·상품준비중 탭의
     * 액션이다(§34-3) — <b>결제된 작업 큐(NEW·PREPARING)</b>만 싣는다. 결제 전이나 발송 이후 주문의 수취인·연락처·주소를
     * 다시 반출하지 않는다(§34-11). 상한 판정을 위해 상한 + 1건까지만 읽는다.
     */
    private List<SellerOrderRow> collectTargets(SellerScope scope, PurchaseOrderRequest request, LocalDateTime now) {
        if (request.deliveryGroupIds() != null && !request.deliveryGroupIds().isEmpty()) {
            return deliveryGroupRepository.findAllOwned(new LinkedHashSet<>(request.deliveryGroupIds()),
                            scope.market().getId()).stream()
                    .filter(group -> group.getOrder().getPaidAt() != null
                            && FulfillmentStatus.WORKABLE.contains(group.getFulfillmentStatus()))
                    .map(group -> new SellerOrderRow(group, group.getOrder().getOrderNumber(),
                            group.getOrder().getPaidAt(), group.getOrder().getRecipientName(),
                            queryService.resolveGroupBuyTitle(group)))
                    .toList();
        }
        OrderTab tab = request.tab() == null ? OrderTab.NEW : request.tab();
        SellerOrderSearchCondition condition = queryService.buildCondition(scope.market().getId(), tab,
                request.dateBasis(), request.from(), request.to(), request.searchType(), request.keyword(), null, now);
        if (tab != OrderTab.NEW && tab != OrderTab.PREPARING) {
            return List.of(); // 발주서가 없는 탭 — 조건 검증(기간 상한 등)만 하고 대상은 없다
        }
        int limit = orderProperties.getPurchaseOrderMaxGroups() + 1;
        return deliveryGroupRepository.searchForSeller(condition, PageRequest.of(0, limit)).getContent();
    }

    // ------------------------------------------------------------------ 업로드 분류(E3)

    private Map<String, List<OrderDeliveryGroup>> groupByOrderNumber(SellerScope scope,
                                                                     List<ShipmentExcelParser.RawRow> rows) {
        Set<String> orderNumbers = rows.stream()
                .map(ShipmentExcelParser.RawRow::orderNumber)
                .filter(n -> !n.isEmpty() && !SUB_ORDER_PATTERN.matcher(n).matches())
                .collect(Collectors.toSet());
        if (orderNumbers.isEmpty()) {
            return Map.of();
        }
        return deliveryGroupRepository.findByOrderNumbers(scope.market().getId(), orderNumbers).stream()
                .collect(Collectors.groupingBy(g -> g.getOrder().getOrderNumber()));
    }

    private Map<String, OrderDeliveryGroup> groupBySubOrderNumber(SellerScope scope,
                                                                  List<ShipmentExcelParser.RawRow> rows) {
        Set<String> subOrderNumbers = rows.stream()
                .map(ShipmentExcelParser.RawRow::orderNumber)
                .filter(n -> SUB_ORDER_PATTERN.matcher(n).matches())
                .collect(Collectors.toSet());
        if (subOrderNumbers.isEmpty()) {
            return Map.of();
        }
        return deliveryGroupRepository.findBySubOrderNumbers(scope.market().getId(), subOrderNumbers).stream()
                .collect(Collectors.toMap(OrderDeliveryGroup::getSubOrderNumber, Function.identity(), (a, b) -> a));
    }

    private Map<String, OrderDeliveryGroup> activeInvoiceOwners(List<ShipmentExcelParser.RawRow> rows) {
        Set<String> trackingNumbers = rows.stream()
                .map(raw -> normalize(raw.trackingNumber()))
                .filter(tn -> !tn.isEmpty())
                .collect(Collectors.toSet());
        if (trackingNumbers.isEmpty()) {
            return Map.of();
        }
        return deliveryGroupRepository.findActiveByTrackingNumbers(trackingNumbers, FulfillmentStatus.INVOICE_ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        g -> (g.getCarrier() == null ? "" : g.getCarrier().name()) + ":" + g.getTrackingNumber(),
                        Function.identity(), (a, b) -> a));
    }

    private Set<Long> pendingCancelIds(Map<String, List<OrderDeliveryGroup>> byOrderNumber,
                                       Map<String, OrderDeliveryGroup> bySubOrderNumber) {
        Set<Long> ids = new HashSet<>();
        byOrderNumber.values().forEach(groups -> groups.forEach(g -> ids.add(g.getId())));
        bySubOrderNumber.values().forEach(g -> ids.add(g.getId()));
        if (ids.isEmpty()) {
            return Set.of();
        }
        return cancelRequestRepository.findPendingByDeliveryGroupIds(ids).stream()
                .map(r -> r.getDeliveryGroup().getId())
                .collect(Collectors.toSet());
    }

    private ShipmentParseResponse.Row classify(SellerScope scope, ShipmentExcelParser.RawRow raw,
                                               Map<String, List<OrderDeliveryGroup>> byOrderNumber,
                                               Map<String, OrderDeliveryGroup> bySubOrderNumber,
                                               Map<String, OrderDeliveryGroup> activeInvoices,
                                               Set<Long> pendingCancelIds,
                                               Set<String> seenInFile,
                                               Set<Long> seenGroups) {
        String trackingNumber = normalize(raw.trackingNumber());
        DeliveryCarrier carrier = raw.carrierText().isEmpty() ? null : DeliveryCarrier.fromLabel(raw.carrierText());

        OrderDeliveryGroup group;
        if (SUB_ORDER_PATTERN.matcher(raw.orderNumber()).matches()) {
            group = bySubOrderNumber.get(raw.orderNumber());
        } else {
            List<OrderDeliveryGroup> candidates = byOrderNumber.getOrDefault(raw.orderNumber(), List.of());
            if (candidates.size() > 1) {
                return error(raw, carrier, trackingNumber, "AMBIGUOUS_ORDER",
                        "하위주문이 여러 건입니다. 하위주문번호로 입력해 주세요.");
            }
            group = candidates.isEmpty() ? null : candidates.get(0);
        }
        if (raw.orderNumber().isEmpty() || group == null) {
            return error(raw, carrier, trackingNumber, "ORDER_NOT_FOUND", "주문번호가 없습니다.");
        }
        if (trackingNumber.isEmpty()) {
            return error(raw, carrier, trackingNumber, "TRACKING_REQUIRED", "송장번호가 없습니다.");
        }
        // 택배사 칸이 비면 carrier: null 로 통과시킨다 — 「채우기」 뒤 목록 셀(택배사 일괄 적용)에서 고르고 확정한다.
        // 미선택 차단은 확정 단계(FE ② · POST /shipments 의 @NotNull)의 몫이다.
        if (!raw.carrierText().isEmpty() && (carrier == null || !carrier.isSelectable())) {
            return error(raw, null, trackingNumber, "CARRIER_INVALID",
                    "지원하지 않는 택배사입니다. 목록의 택배사로 입력해 주세요.");
        }
        // 신규는 업로드에서 제외한다(rev.7) — 소비자 단순 취소권이 살아 있고, 배송중 → 준비중 복귀 경로가 없다.
        switch (group.getFulfillmentStatus()) {
            case NEW -> {
                return error(raw, carrier, trackingNumber, "NEW_NOT_ALLOWED",
                        "신규(준비 대기) 주문 · 준비 시작 전이라 송장을 등록할 수 없습니다.");
            }
            case SHIPPING, RETURNING, DELIVERED, CONFIRMED -> {
                return error(raw, carrier, trackingNumber, "ALREADY_SHIPPED", "이미 배송중인 주문입니다.");
            }
            case CANCELLED, PENDING -> {
                return error(raw, carrier, trackingNumber, "STATE_INVALID", "송장을 등록할 수 없는 상태입니다.");
            }
            case PREPARING -> { /* 정상 */ }
        }
        if (pendingCancelIds.contains(group.getId())) {
            return error(raw, carrier, trackingNumber, "CANCEL_REQUEST_PENDING",
                    "검토 중인 취소 요청이 있는 주문입니다.");
        }
        String invoiceKey = (carrier == null ? "" : carrier.name()) + ":" + trackingNumber;
        OrderDeliveryGroup owner = activeInvoices.get(invoiceKey);
        if (owner != null && !owner.getOrder().getOrderNumber().equals(group.getOrder().getOrderNumber())) {
            return error(raw, carrier, trackingNumber, "INVOICE_DUPLICATE", duplicateMessage(owner, scope));
        }
        // 같은 하위주문이 두 번 — 둘 다 정상으로 내려가면 확정에서 하나가 「상태 변경」으로 빠져 원인을 알 수 없다.
        if (seenGroups.contains(group.getId())) {
            return error(raw, carrier, trackingNumber, "ORDER_DUPLICATE_IN_FILE",
                    "파일 안에서 같은 주문이 두 번 입력되었습니다.");
        }
        if (!seenInFile.add(invoiceKey)) {
            return error(raw, carrier, trackingNumber, "INVOICE_DUPLICATE", "파일 안에서 중복된 송장번호입니다.");
        }
        seenGroups.add(group.getId());
        return new ShipmentParseResponse.Row(raw.rowNumber(), group.getOrder().getOrderNumber(),
                group.getSubOrderNumber(), group.getId(), carrier, trackingNumber, true, null, null);
    }

    private ShipmentParseResponse.Row error(ShipmentExcelParser.RawRow raw, DeliveryCarrier carrier,
                                            String trackingNumber, String code, String message) {
        return new ShipmentParseResponse.Row(raw.rowNumber(), raw.orderNumber(), null, null, carrier, trackingNumber,
                false, code, message);
    }

    private String normalize(String trackingNumber) {
        return trackingNumber == null ? "" : trackingNumber.replaceAll("[^0-9]", "");
    }
}
