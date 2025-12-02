package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Advanced fuzzy search implementation for IntelliSense completion items.
 * Features:
 * - Character matching with position weight
 * - Camel case matching
 * - Prefix matching bonus
 * - Substring matching
 * - Levenshtein distance calculation
 */
public class FuzzySearch {

    private static final int PREFIX_BONUS = 15;      // Bonus for prefix matches
    private static final int CAMEL_BONUS = 10;       // Bonus for camel case matches
    private static final int CONSECUTIVE_BONUS = 15;  // Bonus for consecutive matches
    private static final int SEPARATOR_BONUS = 30;   // Bonus for matches after separators
    private static final int LEADING_LETTER_PENALTY = -5; // Penalty for leading letter
    private static final int MAX_LEADING_LETTER_PENALTY = -15; // Max penalty for leading letters
    private static final int UNMATCHED_LETTER_PENALTY = -1;   // Penalty for each unmatched letter

    /**
     * Performs fuzzy search on completion items
     */
    public static List<CompletionItem> search(String query, List<CompletionItem> items) {
        return search(query, items, new SearchOptions());
    }

    /**
     * Performs fuzzy search on completion items with options
     */
    public static List<CompletionItem> search(String query, List<CompletionItem> items, SearchOptions options) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(items);
        }

        // Use performance profiler if enabled
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        try (AutoCloseable timer = profiler.startOperation("fuzzy-search")) {

            // Check cache first if available
            if (options.cache != null) {
                List<CompletionItem> cachedResults = options.cache.getCachedSearchResults(query, items);
                if (cachedResults != null) {
                    return cachedResults;
                }
            }

            String normalizedQuery = query.toLowerCase().trim();

            List<CompletionItem> results = items.stream()
                    .parallel() // Use parallel processing for large datasets
                    .map(item -> new ScoredItem(item, calculateScore(normalizedQuery, item, options)))
                    .filter(scoredItem -> scoredItem.score >= options.minScore)
                    .sorted((a, b) -> {
                        // Sort by score descending, then by original relevance, then by label length
                        int scoreComparison = Integer.compare(b.score, a.score);
                        if (scoreComparison != 0) return scoreComparison;

                        int relevanceComparison = Integer.compare(b.item.getRelevanceScore(), a.item.getRelevanceScore());
                        if (relevanceComparison != 0) return relevanceComparison;

                        return Integer.compare(a.item.getLabel().length(), b.item.getLabel().length());
                    })
                    .limit(options.maxResults) // Limit results for performance
                    .map(scoredItem -> scoredItem.item)
                    .collect(Collectors.toList());

            // Cache results if cache is available
            if (options.cache != null) {
                options.cache.cacheSearchResults(query, items, results);
            }

            return results;
        } catch (Exception e) {
            // Handle profiler exceptions gracefully - fallback to basic search
            String normalizedQuery = query.toLowerCase().trim();

            return items.stream()
                    .map(item -> new ScoredItem(item, calculateScore(normalizedQuery, item, options)))
                    .filter(scoredItem -> scoredItem.score >= options.minScore)
                    .sorted((a, b) -> Integer.compare(b.score, a.score))
                    .limit(options.maxResults)
                    .map(scoredItem -> scoredItem.item)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Calculates fuzzy match score for a query against an item
     */
    private static int calculateScore(String query, CompletionItem item) {
        return calculateScore(query, item, new SearchOptions());
    }

    /**
     * Calculates fuzzy match score for a query against an item with options
     */
    private static int calculateScore(String query, CompletionItem item, SearchOptions options) {
        String target = item.getLabel().toLowerCase();

        // Exact match gets highest score
        if (target.equals(query)) {
            return 1000 + item.getRelevanceScore();
        }

        // Prefix match gets high score
        if (target.startsWith(query)) {
            return 800 + PREFIX_BONUS + item.getRelevanceScore();
        }

        // Contains match gets medium score
        if (target.contains(query)) {
            return 600 + item.getRelevanceScore();
        }

        // Fuzzy match calculation
        int fuzzyScore = calculateFuzzyScore(query, target);
        if (fuzzyScore <= 0) {
            return 0; // No match
        }

        return fuzzyScore + item.getRelevanceScore();
    }

    /**
     * Advanced fuzzy matching algorithm
     */
    public static int calculateFuzzyScore(String query, String target) {
        if (query.isEmpty()) return 100;
        if (target.isEmpty()) return 0;

        int queryLen = query.length();
        int targetLen = target.length();

        // Simple case: query longer than target
        if (queryLen > targetLen) {
            return 0;
        }

        char[] queryChars = query.toCharArray();
        char[] targetChars = target.toCharArray();

        int score = 100; // Base score
        int queryIndex = 0;
        int consecutiveMatches = 0;
        boolean previousWasMatch = false;

        for (int targetIndex = 0; targetIndex < targetLen && queryIndex < queryLen; targetIndex++) {
            char targetChar = targetChars[targetIndex];
            char queryChar = queryChars[queryIndex];

            if (targetChar == queryChar) {
                queryIndex++;

                // Bonus for consecutive matches
                if (previousWasMatch) {
                    consecutiveMatches++;
                    score += CONSECUTIVE_BONUS;
                } else {
                    consecutiveMatches = 1;
                }

                // Bonus for matches at word boundaries
                if (targetIndex == 0 || isWordBoundary(targetChars, targetIndex)) {
                    score += SEPARATOR_BONUS;
                }

                // Bonus for camel case matches
                if (targetIndex > 0 && Character.isUpperCase(targetChar) &&
                        Character.isLowerCase(targetChars[targetIndex - 1])) {
                    score += CAMEL_BONUS;
                }

                previousWasMatch = true;
            } else {
                // Penalty for unmatched letters
                if (queryIndex > 0) { // Only penalize after we've started matching
                    score += UNMATCHED_LETTER_PENALTY;
                }

                consecutiveMatches = 0;
                previousWasMatch = false;
            }
        }

        // Must match all query characters
        if (queryIndex < queryLen) {
            return 0;
        }

        // Penalty for leading unmatched letters
        int leadingLetters = findFirstMatchIndex(query, target);
        if (leadingLetters > 0) {
            int penalty = Math.max(leadingLetters * LEADING_LETTER_PENALTY, MAX_LEADING_LETTER_PENALTY);
            score += penalty;
        }

        return Math.max(score, 1); // Minimum score of 1 for any match
    }

    /**
     * Finds the index of the first character match
     */
    private static int findFirstMatchIndex(String query, String target) {
        if (query.isEmpty()) return 0;

        char firstQueryChar = query.charAt(0);
        return target.indexOf(firstQueryChar);
    }

    /**
     * Checks if a position is at a word boundary
     */
    private static boolean isWordBoundary(char[] chars, int index) {
        if (index == 0) return true;

        char current = chars[index];
        char previous = chars[index - 1];

        return !Character.isLetterOrDigit(previous) ||
                (Character.isUpperCase(current) && Character.isLowerCase(previous)) ||
                (Character.isDigit(current) && !Character.isDigit(previous)) ||
                (!Character.isDigit(current) && Character.isDigit(previous));
    }

    /**
     * Calculates Levenshtein distance for additional matching
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[][] matrix = new int[len1 + 1][len2 + 1];

        // Initialize first row and column
        for (int i = 0; i <= len1; i++) {
            matrix[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            matrix[0][j] = j;
        }

        // Fill the matrix
        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);

                if (c1 == c2) {
                    matrix[i][j] = matrix[i - 1][j - 1];
                } else {
                    matrix[i][j] = 1 + Math.min(
                            Math.min(matrix[i - 1][j], matrix[i][j - 1]),
                            matrix[i - 1][j - 1]
                    );
                }
            }
        }

        return matrix[len1][len2];
    }

    /**
     * Advanced search with multiple criteria
     */
    public static List<CompletionItem> advancedSearch(String query, List<CompletionItem> items, SearchOptions options) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(items);
        }

        String normalizedQuery = query.toLowerCase().trim();

        // Use performance profiler if enabled
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        try (AutoCloseable timer = profiler.startOperation("advanced-search")) {

            return items.stream()
                    .parallel() // Use parallel processing for large datasets
                    .map(item -> {
                        int score = calculateAdvancedScore(normalizedQuery, item, options);
                        return new ScoredItem(item, score);
                    })
                    .filter(scoredItem -> scoredItem.score > options.minimumScore)
                    .sorted((a, b) -> {
                        int scoreComparison = Integer.compare(b.score, a.score);
                        if (scoreComparison != 0) return scoreComparison;

                        // Secondary sort by type priority
                        int typePriorityA = getTypePriority(a.item.getType());
                        int typePriorityB = getTypePriority(b.item.getType());
                        int typeComparison = Integer.compare(typePriorityB, typePriorityA);
                        if (typeComparison != 0) return typeComparison;

                        return Integer.compare(a.item.getLabel().length(), b.item.getLabel().length());
                    })
                    .limit(options.maxResults)
                    .map(scoredItem -> scoredItem.item)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Handle profiler exceptions gracefully - fallback to basic search
            return items.stream()
                    .map(item -> {
                        int score = calculateAdvancedScore(normalizedQuery, item, options);
                        return new ScoredItem(item, score);
                    })
                    .filter(scoredItem -> scoredItem.score > options.minimumScore)
                    .sorted((a, b) -> Integer.compare(b.score, a.score))
                    .limit(options.maxResults)
                    .map(scoredItem -> scoredItem.item)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Calculates advanced score with additional criteria
     */
    private static int calculateAdvancedScore(String query, CompletionItem item, SearchOptions options) {
        int baseScore = calculateScore(query, item);
        int descriptionScore = 0;
        int dataTypeScore = 0;

        // Search in description if enabled - check even if label doesn't match
        if (options.searchInDescription && item.getDescription() != null) {
            if (item.getDescription().toLowerCase().contains(query)) {
                descriptionScore = 80; // Significant score for description match
            }
        }

        // Search in data type if enabled - check even if label doesn't match
        if (options.searchInDataType && item.getDataType() != null) {
            if (item.getDataType().toLowerCase().contains(query)) {
                dataTypeScore = 70; // Score for data type match
            }
        }

        // If label doesn't match but description or dataType does, use those scores
        if (baseScore <= 0) {
            if (descriptionScore > 0 || dataTypeScore > 0) {
                baseScore = Math.max(descriptionScore, dataTypeScore);
            } else {
                return 0;
            }
        } else {
            // Add as bonus when label also matches
            baseScore += (descriptionScore > 0 ? 30 : 0);
            baseScore += (dataTypeScore > 0 ? 25 : 0);
        }

        // Bonus for required items
        if (item.isRequired()) {
            baseScore += 50;
        }

        // Bonus for items with specific types
        baseScore += getTypePriority(item.getType()) * 10;

        // Bonus for items with documentation
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            baseScore += 20;
        }

        return baseScore;
    }

    /**
     * Gets priority for different completion types
     */
    private static int getTypePriority(CompletionItemType type) {
        switch (type) {
            case ELEMENT:
                return 10;
            case ATTRIBUTE:
                return 8;
            case SNIPPET:
                return 6;
            case TEXT:
                return 4;
            case NAMESPACE:
                return 2;
            default:
                return 1;
        }
    }

    /**
     * Search options for performance-optimized search
     */
    public static class SearchOptions {
        public int maxResults = 50;
        public int minScore = 10;
        public int minimumScore = 10; // Keep for backward compatibility
        public boolean searchInDescription = true;
        public boolean searchInDataType = true;
        public boolean includeDescription = true;
        public boolean includeDataType = true;
        public boolean caseSensitive = false;

        // Performance optimization settings
        public CompletionCache cache = null;
        public boolean useParallelProcessing = true;

        // Scoring weights
        public int labelWeight = 3;
        public int descriptionWeight = 1;
        public int dataTypeWeight = 1;

        public SearchOptions() {
        }

        public SearchOptions maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public SearchOptions minScore(int minScore) {
            this.minScore = minScore;
            this.minimumScore = minScore; // Keep both in sync
            return this;
        }

        public SearchOptions minimumScore(int minimumScore) {
            this.minimumScore = minimumScore;
            this.minScore = minimumScore; // Keep both in sync
            return this;
        }

        public SearchOptions searchInDescription(boolean search) {
            this.searchInDescription = search;
            this.includeDescription = search;
            return this;
        }

        public SearchOptions searchInDataType(boolean search) {
            this.searchInDataType = search;
            this.includeDataType = search;
            return this;
        }

        public SearchOptions withCache(CompletionCache cache) {
            this.cache = cache;
            return this;
        }

        public SearchOptions parallelProcessing(boolean enabled) {
            this.useParallelProcessing = enabled;
            return this;
        }

        public SearchOptions labelWeight(int weight) {
            this.labelWeight = weight;
            return this;
        }

        public SearchOptions caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }
    }

    /**
         * Helper class for scored items
         */
        private record ScoredItem(CompletionItem item, int score) {
    }

    /**
     * Highlights matching characters in a string
     */
    public static String highlightMatches(String text, String query) {
        if (query == null || query.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        int queryIndex = 0;
        int textIndex = 0;
        for (; textIndex < text.length() && queryIndex < query.length(); textIndex++) {
            if (lowerText.charAt(textIndex) == lowerQuery.charAt(queryIndex)) {
                result.append("<mark>").append(text.charAt(textIndex)).append("</mark>");
                queryIndex++;
            } else {
                result.append(text.charAt(textIndex));
            }
        }

        // Append remaining characters from text that weren't processed
        if (textIndex < text.length()) {
            result.append(text.substring(textIndex));
        }

        return result.toString();
    }
}