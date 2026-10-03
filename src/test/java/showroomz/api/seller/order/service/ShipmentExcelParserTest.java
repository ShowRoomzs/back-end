package showroomz.api.seller.order.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.order.service.ShipmentExcelParser.RawRow;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("송장 엑셀 양식·파싱(§34-5) — 셀 값을 읽기만 한다. 분류·확정은 커맨드 서비스 몫")
class ShipmentExcelParserTest {

    private final ShipmentExcelParser parser = new ShipmentExcelParser();

    @Test
    @DisplayName("양식은 헤더 3칸(주문번호 · 택배사 · 송장번호)만 있는 시트 1장")
    void template() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(parser.template()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("송장 업로드");
            assertThat(sheet.getLastRowNum()).isZero();
            Row header = sheet.getRow(0);
            assertThat(List.of(header.getCell(0).getStringCellValue(), header.getCell(1).getStringCellValue(),
                    header.getCell(2).getStringCellValue())).containsExactly("주문번호", "택배사", "송장번호");
        }
    }

    @Test
    @DisplayName("헤더·빈 행은 건너뛰고 앞뒤 공백을 다듬는다 — 행 번호는 엑셀 표기(헤더 포함 1부터)")
    void parsesRowsWithExcelRowNumbers() {
        byte[] file = workbook(sheet -> {
            row(sheet, 0, "주문번호", "택배사", "송장번호");
            row(sheet, 1, " 20261003-000001 ", "CJ대한통운", " 1234-5678-9012 ");
            row(sheet, 2, "", "", "");
            row(sheet, 4, "20261003-000002-01", "한진택배", "");
        });

        List<RawRow> rows = parser.parse(new ByteArrayInputStream(file), 1_000);

        assertThat(rows).containsExactly(
                new RawRow(2, "20261003-000001", "CJ대한통운", "1234-5678-9012"),
                new RawRow(5, "20261003-000002-01", "한진택배", ""));
    }

    @Test
    @DisplayName("숫자 셀로 들어온 12자리 송장번호도 자릿수 그대로 읽는다 — 지수 표기(1.23457E+11)로 뭉개지면 안 된다")
    void numericTrackingNumberKeepsAllDigits() {
        byte[] file = workbook(sheet -> {
            row(sheet, 0, "주문번호", "택배사", "송장번호");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("20261003-000001");
            row.createCell(1).setCellValue("CJ대한통운");
            row.createCell(2).setCellValue(123456789012d);
        });

        List<RawRow> rows = parser.parse(new ByteArrayInputStream(file), 1_000);

        assertThat(rows).singleElement()
                .satisfies(row -> assertThat(row.trackingNumber()).isEqualTo("123456789012"));
    }

    @Test
    @DisplayName("행 상한 — 상한까지는 받고 넘으면 SHIPMENT_FILE_TOO_MANY_ROWS")
    void rowLimit() {
        byte[] file = workbook(sheet -> {
            row(sheet, 0, "주문번호", "택배사", "송장번호");
            for (int i = 1; i <= 3; i++) {
                row(sheet, i, "ORD-" + i, "CJ대한통운", "10000000000" + i);
            }
        });

        assertThat(parser.parse(new ByteArrayInputStream(file), 3)).hasSize(3);
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(file), 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHIPMENT_FILE_TOO_MANY_ROWS);
    }

    @Test
    @DisplayName("xlsx 가 아니면 SHIPMENT_FILE_INVALID — 양식 안내로 돌려보낸다")
    void invalidFile() {
        assertThatThrownBy(() -> parser.parse(
                new ByteArrayInputStream("주문번호,택배사,송장번호".getBytes(StandardCharsets.UTF_8)), 1_000))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHIPMENT_FILE_INVALID);
    }

    // ------------------------------------------------------------------ 보조

    private static byte[] workbook(Consumer<Sheet> filler) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filler.accept(workbook.createSheet("송장 업로드"));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c]);
        }
    }
}
