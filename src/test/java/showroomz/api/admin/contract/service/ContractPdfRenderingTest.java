package showroomz.api.admin.contract.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

@Tag("contract-pdf")
class ContractPdfRenderingTest {
    @ParameterizedTest
    @CsvSource({"1,0,false", "1,0,true", "1,10000000,false", "1,10000000,true",
            "10,0,false", "10,0,true", "10,10000000,false", "10,10000000,true"})
    void keepsSignatureOnIndependentFinalPageAndEmbedsFonts(int items, int fee, boolean note) throws Exception {
        var c = ContractPdfTemplateTest.fixture(items, fee, true, note, true, true, false);
        byte[] bytes = new ContractPdfRenderer("").render(ContractPdfTemplateTest.template().render(c), c.getContractNumber());
        Path artifact = Path.of("build", "contract-pdf", "contract-" + items + "-" + fee + "-" + note + ".pdf");
        Files.createDirectories(artifact.getParent()); Files.write(artifact, bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(6);
            assertThat(pdf.isEncrypted()).isFalse();
            int total = pdf.getNumberOfPages();
            StringBuilder pageReport = new StringBuilder();
            for (int i = 0; i < total; i++) {
                PDFTextStripper text = new PDFTextStripper(); text.setStartPage(i + 1); text.setEndPage(i + 1);
                String pageText = text.getText(pdf).replaceAll("\\s+", " ");
                pageReport.append(i + 1).append(": ").append(pageText).append('\n');
                assertThat(pageText).contains(c.getContractNumber(), (i + 1) + " / " + total);
                if (i == total - 1) assertThat(pageText).contains("서 명 란").doesNotContain("제23조");
                else assertThat(pageText).doesNotContain("서 명 란");
                var page = pdf.getPage(i);
                assertThat(page.getMediaBox().getWidth()).isBetween(594f, 597f);
                for (var fontName : page.getResources().getFontNames()) {
                    assertThat(page.getResources().getFont(fontName).isEmbedded()).isTrue();
                }
            }
            Files.writeString(artifact.resolveSibling(artifact.getFileName() + ".txt"), pageReport);
            if (items == 1 && fee == 0 && !note) {
                var images = new org.apache.pdfbox.rendering.PDFRenderer(pdf);
                javax.imageio.ImageIO.write(images.renderImageWithDPI(0, 100), "png", artifact.resolveSibling("first-page.png").toFile());
                javax.imageio.ImageIO.write(images.renderImageWithDPI(total - 1, 100), "png", artifact.resolveSibling("signature-page.png").toFile());
            }
            assertThat(new PDFTextStripper().getText(pdf)).contains("계약 상품 " + items, "김서명", "4,341");
        }
    }
}
