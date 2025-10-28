package com.privacy.privacyanalyzer;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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
        if (website == null) website = "";
        website = website.trim();
        if (!website.matches("(?i)^https?://.*")) {
            website = "https://" + website;
        }

        List<Map<String, String>> cookiesList = new ArrayList<>();
        Set<String> thirdPartyDomains = new HashSet<>();
        List<Map<String, Object>> storageList = new ArrayList<>();
        String pageTitle = "";

        // Setup Chrome options with anti-detection and good defaults
        String chromiumPath = System.getenv().getOrDefault(
                "CHROMIUM_PATH",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
        );

        ChromeOptions options = new ChromeOptions();
        options.setBinary(chromiumPath);

        options.addArguments(
                "--headless=new", // use new headless if available
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-blink-features=AutomationControlled",
                "--window-size=1920,1080",
                "--blink-settings=imagesEnabled=false",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "--disable-extensions",
                "--disable-infobars"
        );

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            // more tolerant page load timeout
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

            // Navigate to target
            driver.get(website);

            // Wait for readyState = complete (longer timeout for big sites)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
            wait.until((ExpectedCondition<Boolean>) wd ->
                    ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete")
            );

            // Apply a few anti-detection tweaks via JS
            JavascriptExecutor js = (JavascriptExecutor) driver;
            try {
                js.executeScript(
                        "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                        "window.navigator.chrome = { runtime: {} };" +
                        "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});" +
                        "Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});"
                );
            } catch (Exception ignored) {}

            // Give async trackers a chance to run
            try { Thread.sleep(3500); } catch (InterruptedException ignored) {}

            // Interact to trigger lazy-loading trackers (scroll slowly)
            try {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight/3);");
                Thread.sleep(700);
                js.executeScript("window.scrollTo(0, document.body.scrollHeight*2/3);");
                Thread.sleep(700);
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {}

            // Optionally wait until cookies appear or timeout (10s)
            long waitStart = System.currentTimeMillis();
            while (driver.manage().getCookies().isEmpty() && (System.currentTimeMillis() - waitStart) < 10_000) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            pageTitle = driver.getTitle() == null || driver.getTitle().isEmpty() ? "Unknown" : driver.getTitle();

            // Extract cookies
            for (org.openqa.selenium.Cookie cookie : driver.manage().getCookies()) {
                cookiesList.add(Map.of(
                        "name", cookie.getName(),
                        "domain", cookie.getDomain(),
                        "path", cookie.getPath(),
                        "value", cookie.getValue()
                ));
            }

            // Extract localStorage/sessionStorage keys
            try {
                @SuppressWarnings("unchecked")
                List<String> lsKeys = (List<String>) js.executeScript("return Object.keys(localStorage || {});");
                @SuppressWarnings("unchecked")
                List<String> ssKeys = (List<String>) js.executeScript("return Object.keys(sessionStorage || {});");
                if (lsKeys != null && !lsKeys.isEmpty()) storageList.add(Map.of("type", "localStorage", "keys", lsKeys));
                if (ssKeys != null && !ssKeys.isEmpty()) storageList.add(Map.of("type", "sessionStorage", "keys", ssKeys));
            } catch (Exception ignored) {}

            // Collect resource URLs from common elements (script/img/iframe/link)
            String mainDomain = null;
            try {
                URI u = new URI(website);
                mainDomain = u.getHost();
            } catch (Exception ignored) {}

            try {
                @SuppressWarnings("unchecked")
                List<String> resourceUrls = (List<String>) js.executeScript(
                        "const urls = [];" +
                        "document.querySelectorAll('script[src],img[src],iframe[src],link[href]').forEach(e => {" +
                        "  urls.push(e.src || e.href);" +
                        "});" +
                        "return urls.slice(0,200);"
                );
                if (resourceUrls != null) {
                    for (String url : resourceUrls) {
                        if (url == null || url.isBlank()) continue;
                        try {
                            URI ru = new URI(url);
                            String host = ru.getHost();
                            if (host != null && mainDomain != null && !host.equalsIgnoreCase(mainDomain)) {
                                thirdPartyDomains.add(host.startsWith("www.") ? host.substring(4) : host);
                            } else if (host != null && mainDomain == null) {
                                thirdPartyDomains.add(host.startsWith("www.") ? host.substring(4) : host);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            // Additional heuristic: check inline scripts for known vendor domains (quick scan)
            try {
                String inlineScripts = (String) js.executeScript(
                        "let s = '';" +
                        "document.querySelectorAll('script').forEach(x => { if (!x.src) s += x.innerText + ' ';});" +
                        "return s.slice(0,20000);"
                );
                if (inlineScripts != null) {
                    String lower = inlineScripts.toLowerCase();
                    List<String> common = Arrays.asList("google-analytics.com", "googletagmanager", "doubleclick.net", "facebook.net", "connect.facebook.net", "googleadservices.com", "adsystem.com");
                    for (String vendor : common) {
                        if (lower.contains(vendor)) {
                            thirdPartyDomains.add(vendor);
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            // Log for debugging; do not rethrow to keep response stable
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
        }

        // Compute scores
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
        result.put("pageTitle", pageTitle == null || pageTitle.isEmpty() ? "Unknown" : pageTitle);
        result.put("cookiesFound", cookieScore);
        result.put("cookies", cookiesList);
        result.put("thirdPartyFound", thirdPartyScore);
        result.put("thirdPartyDomains", thirdPartyDomains.stream().sorted().collect(Collectors.toList()));
        result.put("storageFound", storageScore);
        result.put("storage", storageList);
        result.put("privacyGrade", privacyGrade);
        result.put("analysisSummary", analysisSummary);
        result.put("examples", examples);
        return result;
    }

    private String generateExamples(int cookies, int trackers, int storage, Set<String> thirdPartyDomains) {
        if (cookies == 0 && trackers == 0) {
            return "Similar to Wikipedia, which collects minimal user data and rarely uses third-party trackers.";
        } else if (trackers > 0 && thirdPartyDomains.stream().anyMatch(d -> d.contains("google") || d.contains("meta") || d.contains("facebook"))) {
            return "This pattern often appears in analytics-driven sites like news outlets that use Google services.";
        } else if (storage > 0) {
            return "Sites using localStorage may store user preferences or session tokens persistently.";
        } else {
            return "Moderate websites use light tracking for improving UX while maintaining transparency.";
        }
    }
}
