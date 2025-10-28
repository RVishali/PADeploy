package com.privacy.privacyanalyzer;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.net.URI;

// Playwright imports
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

@RestController
@CrossOrigin(origins = "https://privacyanalyzer.onrender.com") // Replace with your frontend URL
public class PrivacyAnalyzerController {

    @PostMapping("/analyze")
    public Map<String, Object> analyzeWebsite(@RequestBody Map<String, String> payload) {
        String website = payload.get("website");
        List<Map<String, String>> cookiesList = new ArrayList<>();
        Set<String> thirdPartyDomains = new HashSet<>();
        List<Map<String, Object>> storageList = new ArrayList<>();
        String pageTitle = "";

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            Page page = browser.newPage();
            page.navigate(website, new Page.NavigateOptions().setTimeout(15000));

            pageTitle = page.title();

            // Get cookies
            for (BrowserContext.Cookie cookie : page.context().cookies()) {
                cookiesList.add(Map.of(
                    "name", cookie.name,
                    "domain", cookie.domain,
                    "path", cookie.path,
                    "value", cookie.value
                ));
            }

            // Get LocalStorage and SessionStorage keys
            List<String> localKeys = page.evaluate("() => Object.keys(localStorage)");
            List<String> sessionKeys = page.evaluate("() => Object.keys(sessionStorage)");
            if (!localKeys.isEmpty()) storageList.add(Map.of("type", "localStorage", "keys", localKeys));
            if (!sessionKeys.isEmpty()) storageList.add(Map.of("type", "sessionStorage", "keys", sessionKeys));

            // Get third-party domains from scripts, images, iframes
            String mainDomain = new URI(website).getHost();
            List<String> resourceUrls = page.evalOnSelectorAll(
                "script[src],img[src],iframe[src]",
                "elements => elements.map(e => e.src).slice(0,50)"
            );

            for (String url : resourceUrls) {
                try {
                    String host = new URI(url).getHost();
                    if (host != null && !host.equals(mainDomain)) thirdPartyDomains.add(host);
                } catch (Exception ignored) {}
            }

            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Scoring
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
