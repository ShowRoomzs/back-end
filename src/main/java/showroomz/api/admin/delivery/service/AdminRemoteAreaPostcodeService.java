package showroomz.api.admin.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.delivery.dto.RemoteAreaPostcodeDto;
import showroomz.domain.order.entity.RemoteAreaPostcode;
import showroomz.domain.order.repository.RemoteAreaPostcodeRepository;
import showroomz.domain.order.service.DeliveryRegionResolver;
import showroomz.domain.order.type.DeliveryRegion;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 추가 배송비 권역 우편번호 운영 등록(55 계획서 P4 · D4 — 플랫폼 단일 목록 · 어드민 등록 · 초기 CSV 1회).
 *
 * <p>배송 권역은 결제 시점에 하위주문 스냅샷으로 남는다({@code order_delivery_group.delivery_region · region_surcharge}) — 등록 · 삭제는
 * <b>새 주문부터</b> 반영되고 이미 결제된 주문의 배송비는 바뀌지 않는다. 판정은 {@link DeliveryRegionResolver}가 한다(제주 우선).
 *
 * <p>검증 — 우편번호 5자리 숫자 · 시작 ≤ 끝 · 권역은 도서산간 · 제주만 · 등록 범위와 한 번호라도 겹치면 409. 도서산간 범위가 코드가 아는
 * 제주 범위(63000 ~ 63644)와 겹쳐도 409 다 — 겹치면 제주가 우선이라 등록해도 적용되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminRemoteAreaPostcodeService {

    /** CSV 한 번에 받는 행 수 상한. */
    static final int BULK_MAX_ROWS = 5_000;
    private static final int NAME_MAX = 100;

    private final RemoteAreaPostcodeRepository postcodeRepository;

    @Transactional(readOnly = true)
    public List<RemoteAreaPostcodeDto.Item> list() {
        return postcodeRepository.findAllByOrderByPostcodeFromAscIdAsc().stream().map(RemoteAreaPostcodeDto.Item::of).toList();
    }

    @Transactional
    public RemoteAreaPostcodeDto.Item create(RemoteAreaPostcodeDto.CreateRequest request, LocalDateTime now) {
        Range range = validated(request.postcodeFrom(), request.postcodeTo(), request.region(), request.name(), "");
        List<RemoteAreaPostcode> overlapping = postcodeRepository.findOverlapping(range.from(), range.to());
        if (!overlapping.isEmpty()) {
            throw overlap("", overlapping.getFirst());
        }
        return RemoteAreaPostcodeDto.Item.of(postcodeRepository.save(range.toEntity(now)));
    }

    @Transactional
    public void delete(Long id) {
        RemoteAreaPostcode postcode = postcodeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.REMOTE_AREA_POSTCODE_NOT_FOUND));
        postcodeRepository.delete(postcode);
    }

    /**
     * CSV 일괄 등록 — 한 행이라도 틀리면 하나도 등록하지 않는다. 칸 = 시작 우편번호, 끝 우편번호, 권역, 이름.
     * <ul>
     *     <li>첫 줄의 첫 칸에 숫자가 없으면 머리글로 보고 건너뛴다 · 빈 줄은 건너뛴다</li>
     *     <li>끝 우편번호가 비면 시작과 같다(번호 하나) · 권역이 비면 도서산간 · 권역은 {@code REMOTE · JEJU · 도서산간 · 제주}</li>
     *     <li>이름에 쉼표가 있으면 큰따옴표로 감싼다 · 인코딩은 UTF-8, 아니면 엑셀 기본(MS949)으로 읽는다</li>
     * </ul>
     */
    @Transactional
    public RemoteAreaPostcodeDto.BulkResult bulkCreate(MultipartFile file, LocalDateTime now) {
        List<Range> ranges = parse(file);
        List<Range> byStart = ranges.stream().sorted(Comparator.comparing(Range::from)).toList();
        for (int i = 1; i < byStart.size(); i++) {
            Range previous = byStart.get(i - 1);
            Range current = byStart.get(i);
            if (current.from().compareTo(previous.to()) <= 0) {
                throw new BusinessException(ErrorCode.REMOTE_AREA_POSTCODE_OVERLAP,
                        current.where() + previous.where().replace(": ", "") + "과 범위가 겹칩니다.");
            }
        }
        List<RemoteAreaPostcode> registered = postcodeRepository.findAllByOrderByPostcodeFromAscIdAsc();
        for (Range range : ranges) {
            Optional<RemoteAreaPostcode> hit = registered.stream()
                    .filter(p -> p.getPostcodeFrom().compareTo(range.to()) <= 0 && p.getPostcodeTo().compareTo(range.from()) >= 0)
                    .findFirst();
            if (hit.isPresent()) {
                throw overlap(range.where(), hit.get());
            }
        }
        List<RemoteAreaPostcode> saved = postcodeRepository.saveAll(ranges.stream().map(r -> r.toEntity(now)).toList());
        return new RemoteAreaPostcodeDto.BulkResult(saved.size(), saved.stream().map(RemoteAreaPostcodeDto.Item::of).toList());
    }

    // ------------------------------------------------------------------ 검증

    /** @param where 오류 문구 앞머리 — 일괄 등록이면 「3행: 」 */
    private static Range validated(String from, String to, DeliveryRegion region, String name, String where) {
        if (from == null || !from.matches("\\d{5}") || to == null || !to.matches("\\d{5}")) {
            throw invalid(where + "우편번호는 5자리 숫자여야 합니다.");
        }
        if (from.compareTo(to) > 0) {
            throw invalid(where + "시작 우편번호가 끝 우편번호보다 클 수 없습니다.");
        }
        if (region == null || region == DeliveryRegion.STANDARD) {
            throw invalid(where + "권역은 도서산간(REMOTE) 또는 제주(JEJU)여야 합니다.");
        }
        String trimmed = name == null || name.isBlank() ? null : name.trim();
        if (trimmed != null && trimmed.length() > NAME_MAX) {
            throw invalid(where + "이름은 " + NAME_MAX + "자까지입니다.");
        }
        if (region == DeliveryRegion.REMOTE && DeliveryRegionResolver.overlapsJejuDefault(from, to)) {
            throw new BusinessException(ErrorCode.REMOTE_AREA_POSTCODE_OVERLAP, where
                    + "제주 기본 범위(63000~63644)와 겹칩니다. 겹치는 구간은 제주가 우선이라 도서산간으로 등록해도 적용되지 않습니다.");
        }
        return new Range(from, to, region, trimmed, where);
    }

    private static BusinessException overlap(String where, RemoteAreaPostcode existing) {
        return new BusinessException(ErrorCode.REMOTE_AREA_POSTCODE_OVERLAP, where + "이미 등록된 우편번호 범위와 겹칩니다 — "
                + existing.getPostcodeFrom() + "~" + existing.getPostcodeTo()
                + (existing.getName() == null ? "" : "(" + existing.getName() + ")") + ".");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }

    // ------------------------------------------------------------------ CSV

    private static List<Range> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("CSV 파일을 첨부해 주세요.");
        }
        String text;
        try {
            text = decode(file.getBytes());
        } catch (IOException e) {
            throw invalid("CSV 파일을 읽을 수 없습니다.");
        }
        String[] lines = text.split("\r\n|\n|\r", -1);
        List<Range> ranges = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = cells(line);
            if (first) {
                first = false;
                if (!cells.getFirst().matches(".*\\d.*")) {
                    continue; // 머리글
                }
            }
            String where = (i + 1) + "행: ";
            if (cells.size() > 4) {
                throw invalid(where + "칸은 시작 우편번호, 끝 우편번호, 권역, 이름 넷까지입니다. 이름에 쉼표가 있으면 큰따옴표로 감싸 주세요.");
            }
            String from = cells.getFirst();
            String to = cells.size() > 1 && !cells.get(1).isEmpty() ? cells.get(1) : from;
            DeliveryRegion region = cells.size() > 2 && !cells.get(2).isEmpty() ? region(cells.get(2), where)
                    : DeliveryRegion.REMOTE;
            String name = cells.size() > 3 ? cells.get(3) : null;
            ranges.add(validated(from, to, region, name, where));
            if (ranges.size() > BULK_MAX_ROWS) {
                throw invalid("CSV 일괄 등록은 최대 " + String.format("%,d", BULK_MAX_ROWS) + "행까지 가능합니다.");
            }
        }
        if (ranges.isEmpty()) {
            throw invalid("등록할 행이 없습니다.");
        }
        return ranges;
    }

    private static DeliveryRegion region(String value, String where) {
        String v = value.trim();
        if (v.equals(DeliveryRegion.REMOTE.getLabel())) {
            return DeliveryRegion.REMOTE;
        }
        if (v.equals(DeliveryRegion.JEJU.getLabel())) {
            return DeliveryRegion.JEJU;
        }
        try {
            return DeliveryRegion.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid(where + "권역은 REMOTE · JEJU · 도서산간 · 제주 중 하나여야 합니다.");
        }
    }

    /** UTF-8(BOM 허용)로 읽고, 깨지면 엑셀 기본 저장 인코딩(MS949)으로 다시 읽는다. */
    private static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("MS949"));
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /** CSV 한 줄 → 칸(앞뒤 공백 제거). 큰따옴표로 감싼 칸은 쉼표를 담을 수 있고 {@code ""}는 따옴표 하나다. */
    static List<String> cells(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    private record Range(String from, String to, DeliveryRegion region, String name, String where) {

        RemoteAreaPostcode toEntity(LocalDateTime now) {
            return RemoteAreaPostcode.of(from, to, region, name, now);
        }
    }
}
