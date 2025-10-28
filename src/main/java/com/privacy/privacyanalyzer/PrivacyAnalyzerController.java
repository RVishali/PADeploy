package com.privacy.privacyanalyzer;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PrivacyAnalyzerController {

    @PostMapping("/analyze")
    public Map<String, Object> analyzeWebsite(@RequestBody Map<String, String> payload) {
        String website = payload.get("website");
        List<Map<String, String>> cookiesList = new ArrayList<>();
        Set<String> thirdPartyDomains = new HashSet<>();
        List<Map<String, Object>> storageList = new ArrayList<>();
        String pageTitle = "";

        // Setup Selenium
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1920,1080",
                "--disable-gpu"
        );

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            driver.get(website);

            // Wait until page is fully loaded
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                    (ExpectedCondition<Boolean>) wd ->
                            ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete")
            );

            pageTitle = driver.getTitle();

            // ----------------
            // Cookies
            // ----------------
            for (Cookie cookie : driver.manage().getCookies()) {
                cookiesList.add(Map.of(
                        "name", cookie.getName(),
                        "domain", cookie.getDomain(),
                        "path", cookie.getPath(),
                        "value", cookie.getValue()
                ));
            }

            // ----------------
            // LocalStorage & SessionStorage
            // ----------------
            JavascriptExecutor js = (JavascriptExecutor) driver;
            List<String> localKeys = (List<String>) js.executeScript(
                    "return Object.keys(window.localStorage);"
            );
            List<String> sessionKeys = (List<String>) js.executeScript(
                    "return Object.keys(window.sessionStorage);"
            );
            if (!localKeys.isEmpty()) storageList.add(Map.of("type", "localStorage", "keys", localKeys));
            if (!sessionKeys.isEmpty()) storageList.add(Map.of("type", "sessionStorage", "keys", sessionKeys));

            // ----------------
            // Third-party scripts/images/iframes
            // ----------------
            String mainDomain = new URI(website).getHost();
            List<WebElement> resources = driver.findElements(By.cssSelector("script[src],img[src],iframe[src]"));
            for (WebElement el : resources) {
                try {
                    String src = el.getAttribute("src");
                    if (src != null) {
                        String host = new URI(src).getHost();
                        if (host != null && !host.equals(mainDomain)) thirdPartyDomains.add(host);
                    }
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }

        // ----------------
        // Privacy Scoring
        // ----------------
        int cookieScore = cookiesList.size();
        int thirdPartyScore = thirdPartyDomains.size();
        int storageScore = storageList.size();
        int totalScore = cookieScore * 2 + thirdPartyScore * 3 + storageScore;

        String privacyGrade;
        String analysisSummary;

        if (totalScore == 0) {
            privacyGrade = "A+";
            analysisSummary = "This website is highly privacy-friendly — no cookies, storage, or external trackers were detected.";
        } else if (totalScore <= 5) {
            privacyGrade = "B";
            analysisSummary = "This website uses minimal tracking technologies, typical for functional or analytics purposes.";
        } else if (totalScore <= 15) {
            privacyGrade = "C";
            analysisSummary = "The site uses several trackers, cookies, and possibly analytics tools — moderate privacy risk.";
        } else {
            privacyGrade = "D";
            analysisSummary = "Heavy use of tracking cookies, third-party scripts, or ads detected. High privacy risk.";
        }

        // ----------------
        // Examples generation
        // ----------------
        String examples = generateExamples(cookieScore, thirdPartyScore, storageScore, thirdPartyDomains);

        // ----------------
        // Build Result
        // ----------------
        Map<String, Object> result = new HashMap<>();
        result.put("website", website);
        result.put("pageTitle", pageTitle.isEmpty() ? "Unknown" : pageTitle);
        result.put("cookiesFound", cookieScore);
        result.put("thirdPartyFound", thirdPartyScore);
        result.put("storageFound", storageScore);
        result.put("privacyGrade", privacyGrade);
        result.put("analysisSummary", analysisSummary);
        result.put("examples", examples);
        result.put("thirdPartyDomains", thirdPartyDomains);

        return result;
    }

    private String generateExamples(int cookies, int thirdParty, int storage, Set<String> thirdPartyDomains) {
        StringBuilder sb = new StringBuilder();
        if (cookies > 0) sb.append(cookies).append(" cookies detected. ");
        if (storage > 0) sb.append(storage).append(" storage entries. ");
        if (thirdParty > 0) sb.append("Third-party domains: ").append(String.join(", ", thirdPartyDomains)).append(".");
        if (cookies + storage + thirdParty == 0) sb.append("No tracking detected.");
        return sb.toString();
    }
}
