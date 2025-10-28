package com.privacy.privacyanalyzer;

import java.net.URI;
import java.time.Duration;
import java.util.*;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "https://privacyanalyzer.onrender.com") // Replace with your deployed frontend URL
public class PrivacyAnalyzerController {

    @PostMapping("/analyze")
    public Map<String, Object> analyzeWebsite(@RequestBody Map<String, String> payload) {
        String website = payload.get("website");
        List<Map<String, String>> cookiesList = new ArrayList<>();
        Set<String> thirdPartyDomains = new HashSet<>();
        List<Map<String, Object>> storageList = new ArrayList<>();
        String pageTitle = "";

        ChromeOptions options = new ChromeOptions();
options.setBinary(System.getenv().getOrDefault("CHROMIUM_PATH", "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));  // Ensure you're using the correct path here
options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");

        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));

            driver.get(website);

            // Wait until the page is loaded (title is not empty)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until((ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd)
                    .executeScript("return document.readyState").equals("complete"));

            pageTitle = driver.getTitle();

            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("window.scrollBy(0, document.body.scrollHeight / 2);");

            for (org.openqa.selenium.Cookie cookie : driver.manage().getCookies()) {
                cookiesList.add(Map.of(
                        "name", cookie.getName(),
                        "domain", cookie.getDomain(),
                        "path", cookie.getPath(),
                        "value", cookie.getValue()
                ));
            }

            List<String> lsKeys = (List<String>) js.executeScript("return Object.keys(localStorage);");
            List<String> ssKeys = (List<String>) js.executeScript("return Object.keys(sessionStorage);");
            if (!lsKeys.isEmpty()) storageList.add(Map.of("type", "localStorage", "keys", lsKeys));
            if (!ssKeys.isEmpty()) storageList.add(Map.of("type", "sessionStorage", "keys", ssKeys));

            String mainDomain = new URI(website).getHost();
            List<String> resourceUrls = (List<String>) js.executeScript(
                    "const urls=[];document.querySelectorAll('script[src],img[src],iframe[src]').forEach(e=>{ urls.push(e.src); }); return urls.slice(0,50);");
            for (String url : resourceUrls) {
                try {
                    String host = new URI(url).getHost();
                    if (host != null && !host.equals(mainDomain))
                        thirdPartyDomains.add(host);
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
        }

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

        String examples = generateExamples(cookieScore, thirdPartyScore, storageScore, thirdPartyDomains);

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

    private String generateExamples(int cookies, int trackers, int storage, Set<String> thirdPartyDomains) {
        if (cookies == 0 && trackers == 0) {
            return "Example: Similar to Wikipedia, which collects minimal user data and rarely uses third-party trackers.";
        } else if (trackers > 0 && thirdPartyDomains.stream().anyMatch(d -> d.contains("google") || d.contains("meta"))) {
            return "Example: This pattern often appears in analytics-driven sites like news outlets that use Google services.";
        } else if (storage > 0) {
            return "Example: Sites using localStorage may store user preferences or session tokens persistently.";
        } else {
            return "Example: Moderate websites use light tracking for improving UX while maintaining transparency.";
        }
    }
}
