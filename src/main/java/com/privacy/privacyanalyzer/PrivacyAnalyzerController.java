package com.privacy.privacyanalyzer;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(website, new Page.NavigateOptions().setTimeout(15000));

            pageTitle = page.title();

            // Cookies
            for (Cookie cookie : page.context().cookies()) {
                cookiesList.add(Map.of(
                        "name", cookie.name,
                        "domain", cookie.domain,
                        "path", cookie.path,
                        "value", cookie.value
                ));
            }

            // LocalStorage / SessionStorage
            JSHandle lsKeys = page.evaluateHandle("() => Object.keys(localStorage)");
            JSHandle ssKeys = page.evaluateHandle("() => Object.keys(sessionStorage)");

            List<String> localKeys = (List<String>)(Object) lsKeys.jsonValue();
            List<String> sessionKeys = (List<String>)(Object) ssKeys.jsonValue();

            if (!localKeys.isEmpty()) storageList.add(Map.of("type", "localStorage", "keys", localKeys));
            if (!sessionKeys.isEmpty()) storageList.add(Map.of("type", "sessionStorage", "keys", sessionKeys));

            // Third-party scripts
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

        // Scoring / grading logic
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

    // Dummy example generator, replace with your actual logic
    private String generateExamples(int cookies, int thirdParties, int storage, Set<String> thirdPartyDomains) {
        if (cookies == 0 && thirdParties == 0 && storage == 0) return "Similar to Wikipedia, which collects minimal user data and rarely uses third-party trackers.";
        return "Example: site uses " + cookies + " cookies, " + thirdParties + " third-party domains, and " + storage + " storage entries.";
    }
}
