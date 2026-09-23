package showroomz.api.admin.contract.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Margin;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import showroomz.global.error.exception.*;
import java.nio.file.Path;

@Component
public class ContractPdfRenderer {
    private final String executablePath;
    public ContractPdfRenderer(@Value("${contract.pdf.chromium-executable:}") String executablePath) {
        this.executablePath = executablePath;
    }

    /** Playwright objects are thread-confined; serialize browser launches to bound server memory use. */
    public synchronized byte[] render(String html, String contractNumber) {
        var options = new BrowserType.LaunchOptions().setHeadless(true).setTimeout(30000);
        if (!executablePath.isBlank()) options.setExecutablePath(Path.of(executablePath));
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(java.util.Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = playwright.chromium().launch(options)) {
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false));
            context.route("**/*", route -> route.abort());
            Page page = context.newPage();
            page.setDefaultTimeout(30000);
            page.setContent(html);
            page.evaluate("() => document.fonts.ready");
            return page.pdf(new Page.PdfOptions().setFormat("A4").setPrintBackground(true).setPreferCSSPageSize(true)
                    .setMargin(new Margin().setTop("17mm").setBottom("13mm").setLeft("18mm").setRight("18mm"))
                    .setDisplayHeaderFooter(true).setHeaderTemplate("<span></span>")
                    .setFooterTemplate("<div style='font-size:8px;width:100%;margin:0 18mm;border-top:1px solid #bbb;text-align:right'>"
                            + Entities.escape(contractNumber) + " · <span class='pageNumber'></span> / <span class='totalPages'></span></div>"));
        } catch (PlaywrightException e) {
            throw new BusinessException(ErrorCode.CONTRACT_PDF_GENERATION_FAILED);
        }
    }
}
