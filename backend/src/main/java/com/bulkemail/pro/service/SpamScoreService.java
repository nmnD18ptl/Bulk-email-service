package com.bulkemail.pro.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SpamScoreService {

    private static final List<String> SPAM_WORDS = List.of(
        "free", "winner", "urgent", "act now", "limited time", "click here",
        "guaranteed", "no obligation", "risk free", "special offer", "discount",
        "earn money", "extra income", "make money", "cash bonus", "prize",
        "congratulations", "selected", "exclusive deal", "buy now", "order now",
        "incredible deal", "no cost", "100% free", "satisfaction guaranteed",
        "unbeatable", "bargain", "cheapest", "double your", "earn per week",
        "eliminate debt", "fast cash", "financial freedom", "home based business",
        "incredible offer", "lose weight", "lowest price", "miracle", "online biz",
        "opportunity", "pure profit", "real thing", "saves big money", "stock alert",
        "stop snoring", "subscribe now", "unlimited", "while supplies last"
    );

    private static final Pattern ALL_CAPS_PATTERN = Pattern.compile("[A-Z]{4,}");
    private static final Pattern EXCESSIVE_PUNCT_PATTERN = Pattern.compile("[!?]{2,}");
    private static final Pattern LINK_PATTERN = Pattern.compile("href=", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSUBSCRIBE_PATTERN = Pattern.compile("unsubscribe", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_PATTERN = Pattern.compile("<img", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_PATTERN = Pattern.compile("[a-zA-Z0-9]");

    public SpamAnalysisResult analyze(String subject, String htmlContent, String senderEmail) {
        List<SpamIssue> issues = new ArrayList<>();
        int score = 100;

        // Subject checks
        if (subject != null) {
            // ALL CAPS in subject
            if (ALL_CAPS_PATTERN.matcher(subject).find()) {
                issues.add(new SpamIssue("ALL_CAPS_SUBJECT", "Subject contains all-caps words",
                    Severity.MEDIUM, "Avoid using all-caps in your subject line"));
                score -= 10;
            }

            // Excessive punctuation
            if (EXCESSIVE_PUNCT_PATTERN.matcher(subject).find()) {
                issues.add(new SpamIssue("EXCESSIVE_PUNCTUATION", "Excessive punctuation in subject (!!!, ???)",
                    Severity.MEDIUM, "Use at most one ! or ? in subject line"));
                score -= 10;
            }

            // Spam words in subject
            String subjectLower = subject.toLowerCase();
            List<String> foundSpamWords = new ArrayList<>();
            for (String word : SPAM_WORDS) {
                if (subjectLower.contains(word)) {
                    foundSpamWords.add(word);
                }
            }
            if (!foundSpamWords.isEmpty()) {
                issues.add(new SpamIssue("SPAM_WORDS_SUBJECT",
                    "Spam trigger words in subject: " + String.join(", ", foundSpamWords),
                    Severity.HIGH, "Remove spam trigger words from subject line"));
                score -= foundSpamWords.size() * 5;
            }
        }

        // Content checks
        if (htmlContent != null) {
            String contentLower = htmlContent.toLowerCase();

            // Check unsubscribe link (CRITICAL)
            if (!UNSUBSCRIBE_PATTERN.matcher(contentLower).find()) {
                issues.add(new SpamIssue("MISSING_UNSUBSCRIBE",
                    "No unsubscribe link found (CRITICAL - CAN-SPAM violation)",
                    Severity.CRITICAL, "Add an unsubscribe link: {{UnsubscribeLink}}"));
                score -= 30;
            }

            // Link count
            long linkCount = LINK_PATTERN.matcher(htmlContent).results().count();
            if (linkCount > 10) {
                issues.add(new SpamIssue("TOO_MANY_LINKS",
                    "Too many links (" + linkCount + "). Recommended max: 10",
                    Severity.MEDIUM, "Reduce number of links to under 10"));
                score -= 10;
            } else if (linkCount > 5) {
                issues.add(new SpamIssue("MANY_LINKS",
                    "High number of links (" + linkCount + "). Recommended: under 5",
                    Severity.LOW, "Consider reducing number of links"));
                score -= 5;
            }

            // Image to text ratio
            long imgCount = IMG_PATTERN.matcher(htmlContent).results().count();
            int textLength = TEXT_PATTERN.matcher(htmlContent.replaceAll("<[^>]+>", "")).results()
                .toList().size();
            if (imgCount > 0 && textLength < 100) {
                issues.add(new SpamIssue("IMAGE_TEXT_RATIO",
                    "Too many images relative to text content",
                    Severity.HIGH, "Add more text content. Aim for 60% text, 40% images"));
                score -= 15;
            }

            // ALL CAPS in content
            if (ALL_CAPS_PATTERN.matcher(htmlContent.replaceAll("<[^>]+>", "")).find()) {
                issues.add(new SpamIssue("ALL_CAPS_CONTENT",
                    "All-caps text found in email body",
                    Severity.LOW, "Avoid excessive use of all-caps in email body"));
                score -= 5;
            }

            // Spam words in content
            List<String> foundContentSpamWords = new ArrayList<>();
            for (String word : SPAM_WORDS) {
                if (contentLower.contains(word)) {
                    foundContentSpamWords.add(word);
                }
            }
            if (foundContentSpamWords.size() > 3) {
                issues.add(new SpamIssue("SPAM_WORDS_CONTENT",
                    "Multiple spam trigger words in content: " + String.join(", ", foundContentSpamWords.subList(0, Math.min(5, foundContentSpamWords.size()))),
                    Severity.HIGH, "Remove spam trigger words from email body"));
                score -= foundContentSpamWords.size() * 3;
            }
        }

        score = Math.max(0, Math.min(100, score));
        String rating = score >= 80 ? "GOOD" : score >= 60 ? "WARNING" : "POOR";

        return new SpamAnalysisResult(score, rating, issues);
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public record SpamIssue(String code, String message, Severity severity, String recommendation) {}

    public record SpamAnalysisResult(int score, String rating, List<SpamIssue> issues) {
        public boolean isPoor() { return score < 60; }
        public boolean isGood() { return score >= 80; }
    }
}
