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
        for (Long id : new LinkedHashSet<>(request.deliveryGroupIds())) {
            if (deliveryGroupRepository.startPreparation(id, scope.market().getId(), scope.sellerId(), now) == 1) {
                fulfillmentService.appendHistory(id, FulfillmentEventType.PREPARE_STARTED, FulfillmentActorType.SELLER,
                        scope.sellerId(), null, now);
                succeeded++;
            } else {
                skipped.add(skipReason(id));
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

        List<SellerOrderRow> rows = collectTargets(scope, request, now);
        // 검토 중 취소 요청이 걸린 하위주문은 작업 큐 밖이다 — 발주서에도 싣지 않는다.
        Set<Long> pendingIds = cancelRequestRepository.findPendingByDeliveryGroupIds(
                        rows.stream().map(row -> row.group().getId()).toList()).stream()
                .map(r -> r.getDeliveryGroup().getId())
                .collect(Collectors.toSet());
        rows = rows.stream().filter(row -> !pendingIds.contains(row.group().getId())).toList();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PURCHASE_ORDER_EMPTY);
        }

        Map<Long, List<OrderProduct>> itemsByGroup = orderProductRepository.findByDeliveryGroupIds(
                        rows.stream().map(row -> row.group().getId()).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getDeliveryGroup().getId()));

        // 「다운로드와 함께 준비 시작 처리」 — 기본 ON(§34-4). 발주서를 뽑는 것은 보내겠다는 결정이다.
        int prepared = 0;
        if (request.startPreparationOrDefault()) {
            for (SellerOrderRow row : rows) {
                if (row.group().getFulfillmentStatus() == FulfillmentStatus.NEW
                        && deliveryGroupRepository.startPreparation(row.group().getId(), scope.market().getId(),
                        scope.sellerId(), now) == 1) {
                    fulfillmentService.appendHistory(row.group().getId(), FulfillmentEventType.PREPARE_STARTED,
                            FulfillmentActorType.SELLER, scope.sellerId(), "발주서 다운로드 동시 처리", now);
                    prepared++;
                }
            }
        }

        List<PurchaseOrderExcelWriter.Line> lines = new ArrayList<>();
        for (SellerOrderRow row : rows) {
            OrderDeliveryGroup group = row.group();
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
                .prepareStarted(request.startPreparationOrDefault())
                .downloadedAt(now)
                .build());

        byte[] content = excelWriter.write(columns, lines);
        return new PurchaseOrderFile(content, "발주서_" + FILE_STAMP.format(now) + ".xlsx", rows.size(), prepared);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderTemplateDto.Response getTemplate(String sellerEmail) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        List<PurchaseOrderColumn> columns = templateRepository.findByMarket_Id(scope.market().getId())
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
            String duplicateOrder = findDuplicateOrderNumber(row.carrier(), trackingNumber, row.deliveryGroupId());
            if (duplicateOrder != null) {
                skipped.add(new BatchActionResponse.Skipped(row.deliveryGroupId(), "INVOICE_DUPLICATE",
                        duplicateOrder + "에 이미 등록된 번호입니다."));
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
                skipped.add(skipReason(row.deliveryGroupId()));
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
        Map<String, String> activeInvoices = activeInvoiceOwners(rawRows);
        Set<Long> pendingCancelIds = pendingCancelIds(byOrderNumber, bySubOrderNumber);

        Set<String> seenInFile = new HashSet<>();
        List<ShipmentParseResponse.Row> rows = new ArrayList<>();
        int valid = 0;
        for (ShipmentExcelParser.RawRow raw : rawRows) {
            ShipmentParseResponse.Row parsed = classify(raw, byOrderNumber, bySubOrderNumber, activeInvoices,
                    pendingCancelIds, seenInFile);
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
        if (trackingNumber.isEmpty()
                || tracker.validateInvoice(request.carrier(), trackingNumber) == ValidationResult.INVALID) {
            throw new BusinessException(ErrorCode.INVOICE_FORMAT_INVALID);
        }
        String duplicateOrder = findDuplicateOrderNumber(request.carrier(), trackingNumber, deliveryGroupId);
        if (duplicateOrder != null) {
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
                .stream().collect(Collectors.toMap(OrderDeliveryGroup::getId, Function.identity()));
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
                skipped.add(skipReason(id));
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

    /** 승인(E6) — 요청 항목만 취소 · 남은 항목 발송 · 환불은 운영자 큐 · 단건만(일괄 없음 · §34-8). */
    @Transactional
    public SellerOrderDetailResponse approveCancelRequest(String sellerEmail, Long cancelRequestId) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        OrderCancelRequest request = loadOwnedRequest(cancelRequestId, scope);
        Long groupId = request.getDeliveryGroup().getId();

        if (cancelRequestRepository.approve(cancelRequestId, scope.sellerId(), now) != 1) {
            throw new BusinessException(ErrorCode.CANCEL_REQUEST_ALREADY_DECIDED);
        }
        List<OrderProduct> targets = request.getItems().stream()
                .map(item -> item.getOrderProduct())
                .toList();
        List<OrderProduct> cancelled = fulfillmentService.cancelItemsWithRestock(targets,
                OrderCancelType.REQUEST_APPROVED, now);
        int refundAmount = cancelled.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum();
        // 전 항목 승인일 때만 취소 탭 — 남은 항목이 없으면 그룹도 내리고 배송비까지 전액이다(§34-8).
        if (orderProductRepository.countActiveByGroup(groupId) == 0
                && deliveryGroupRepository.cancelByRequestApproval(groupId, now) == 1) {
            refundAmount += request.getDeliveryGroup().getDeliveryFee();
        }
        fulfillmentService.enqueueRefund(request.getDeliveryGroup(), RefundTaskSource.CANCEL_REQUEST_APPROVED,
                cancelRequestId, refundAmount);
        fulfillmentService.appendHistory(groupId, FulfillmentEventType.CANCEL_REQUEST_APPROVED,
                FulfillmentActorType.SELLER, scope.sellerId(),
                "요청 " + cancelled.size() + "건 취소 · 환불 예정 " + refundAmount + "원", now);
        return queryService.getOrder(sellerEmail, groupId);
    }

    /** 거부(E7) — 사유 필수 · 소비자에게 그대로 전달 · 전 항목 배송 진행. 그룹 상태는 바뀐 적이 없어 복귀가 자동이다. */
    @Transactional
    public SellerOrderDetailResponse rejectCancelRequest(String sellerEmail, Long cancelRequestId,
                                                         CancelRequestRejectRequest request) {
        SellerScope scope = accessGuard.resolve(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        OrderCancelRequest cancelRequest = loadOwnedRequest(cancelRequestId, scope);
        Long groupId = cancelRequest.getDeliveryGroup().getId();

        if (cancelRequestRepository.reject(cancelRequestId, scope.sellerId(), request.reason(), now) != 1) {
            throw new BusinessException(ErrorCode.CANCEL_REQUEST_ALREADY_DECIDED);
        }
        fulfillmentService.appendHistory(groupId, FulfillmentEventType.CANCEL_REQUEST_REJECTED,
                FulfillmentActorType.SELLER, scope.sellerId(), request.reason(), now);
        return queryService.getOrder(sellerEmail, groupId);
    }

    // ------------------------------------------------------------------ 내부

    /** 0행의 사유를 가른다 — 취소 요청이 걸렸는지, 그 사이 상태가 변했는지. */
    private BatchActionResponse.Skipped skipReason(Long deliveryGroupId) {
        if (cancelRequestRepository.existsByDeliveryGroup_IdAndStatus(deliveryGroupId, CancelRequestStatus.PENDING)) {
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

    private String findDuplicateOrderNumber(DeliveryCarrier carrier, String trackingNumber, Long selfId) {
        return deliveryGroupRepository.findActiveByInvoice(carrier, trackingNumber,
                        FulfillmentStatus.INVOICE_ACTIVE).stream()
                .filter(g -> !g.getId().equals(selfId))
                .findFirst()
                .map(g -> g.getOrder().getOrderNumber())
                .orElse(null);
    }

    private void upsertTemplate(SellerScope scope, List<PurchaseOrderColumn> columns) {
        templateRepository.findByMarket_Id(scope.market().getId())
                .ifPresentOrElse(
                        template -> template.update(columns, scope.sellerId()),
                        () -> templateRepository.save(
                                MarketPurchaseOrderTemplate.of(scope.market(), columns, scope.sellerId())));
    }

    /** 발주서 대상 — 선택 건, 선택 없이 열면 현재 탭 전체(목록과 같은 필터 · §34-4). */
    private List<SellerOrderRow> collectTargets(SellerScope scope, PurchaseOrderRequest request, LocalDateTime now) {
        if (request.deliveryGroupIds() != null && !request.deliveryGroupIds().isEmpty()) {
            return deliveryGroupRepository.findAllOwned(request.deliveryGroupIds(), scope.market().getId()).stream()
                    .map(group -> new SellerOrderRow(group, group.getOrder().getOrderNumber(),
                            group.getOrder().getPaidAt(), group.getOrder().getRecipientName(),
                            queryService.resolveGroupBuyTitle(group)))
                    .toList();
        }
        OrderTab tab = request.tab() == null ? OrderTab.NEW : request.tab();
        SellerOrderSearchCondition condition = queryService.buildCondition(scope.market().getId(), tab,
                request.dateBasis(), request.from(), request.to(), request.searchType(), request.keyword(), null, now);
        return deliveryGroupRepository.searchForSeller(condition, PageRequest.of(0, 2000)).getContent();
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

    private Map<String, String> activeInvoiceOwners(List<ShipmentExcelParser.RawRow> rows) {
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
                        g -> g.getOrder().getOrderNumber(), (a, b) -> a));
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

    private ShipmentParseResponse.Row classify(ShipmentExcelParser.RawRow raw,
                                               Map<String, List<OrderDeliveryGroup>> byOrderNumber,
                                               Map<String, OrderDeliveryGroup> bySubOrderNumber,
                                               Map<String, String> activeInvoices,
                                               Set<Long> pendingCancelIds,
                                               Set<String> seenInFile) {
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
        if (!raw.carrierText().isEmpty() && carrier == null) {
            return error(raw, carrier, trackingNumber, "CARRIER_INVALID",
                    "지원하지 않는 택배사입니다. 목록의 11종으로 입력해 주세요.");
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
        String owner = activeInvoices.get(invoiceKey);
        if (owner != null && !owner.equals(group.getOrder().getOrderNumber())) {
            return error(raw, carrier, trackingNumber, "INVOICE_DUPLICATE", owner + "에 이미 등록된 번호입니다.");
        }
        if (!seenInFile.add(invoiceKey)) {
            return error(raw, carrier, trackingNumber, "INVOICE_DUPLICATE", "파일 안에서 중복된 송장번호입니다.");
        }
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
