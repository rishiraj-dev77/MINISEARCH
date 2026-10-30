package com.search;

import com.search.controller.AutocompleteController;
import com.search.controller.SearchController;
import com.search.dao.DocumentDao;
import com.search.dao.SchemaInitializer;
import com.search.dao.SearchHistoryDao;
import com.search.index.InvertedIndex;
import com.search.model.Document;
import com.search.model.QueryStats;
import com.search.model.SearchResult;
import com.search.model.Suggestion;
import com.search.seed.SampleData;
import com.search.service.AutocompleteService;
import com.search.service.IndexService;
import com.search.service.SearchService;
import com.search.service.TrendingService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interactive CLI entry point. Wires the layers (controller -> service -> DAO/index),
 * seeds the DB on first run, and exposes search, autocomplete, trending, and benchmarks.
 *
 * Run with:  mvn exec:java
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Mini Search Engine ===");
        System.out.println("DB: " + com.search.util.DbConnection.getUrl());

        SchemaInitializer.init();
        DocumentDao documentDao = new DocumentDao();
        SearchHistoryDao historyDao = new SearchHistoryDao();

        // seed the DB if empty
        if (documentDao.count() == 0) {
            System.out.println("Seeding sample documents...");
            documentDao.insertAll(SampleData.create());
        }
        System.out.println("Documents in DB: " + documentDao.count());

        // wire layers
        AutocompleteService autocomplete = new AutocompleteService(5);
        IndexService indexService = new IndexService(autocomplete, documentDao);
        SearchService searchService = new SearchService(indexService, autocomplete, historyDao, 1024);
        TrendingService trendingService = new TrendingService(historyDao);
        SearchController searchController = new SearchController(searchService);
        AutocompleteController autocompleteController = new AutocompleteController(autocomplete);

        // build index from DB
        List<Document> docs = indexService.reindex();
        System.out.println("Indexed " + docs.size() + " documents | vocabulary: "
                + indexService.getIndex().vocabularySize() + " terms | trie terms: " + autocomplete.size());
        System.out.println();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        boolean running = true;
        while (running) {
            printMenu();
            String line = in.readLine();
            if (line == null) break;
            String choice = line.trim();
            try {
                switch (choice) {
                    case "1" -> doSearch(in, searchController);
                    case "2" -> doSearchOr(in, searchController);
                    case "3" -> doAutocomplete(in, autocompleteController);
                    case "4" -> printTrending(trendingService.trendingAllTime(10), "Trending (all time)");
                    case "5" -> printTrending(trendingService.trendingLast24h(10), "Trending (last 24h)");
                    case "6" -> printTrending(trendingService.zeroResultQueries(10), "Zero-result queries");
                    case "7" -> printTrending(trendingService.categoryCounts(), "Documents by category");
                    case "8" -> {
                        System.out.println("Reindexing from DB...");
                        List<Document> re = indexService.reindex();
                        System.out.println("Reindexed " + re.size() + " documents.");
                    }
                    case "9" -> runBenchmark(in, searchService, documentDao, indexService, autocomplete);
                    case "10" -> runConcurrentDemo(in, searchService);
                    case "11" -> printStats(searchService, indexService, autocomplete);
                    case "0", "q", "exit" -> {
                        running = false;
                        searchService.close();
                        System.out.println("Bye!");
                    }
                    default -> System.out.println("Unknown option.");
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
                ex.printStackTrace();
            }
            System.out.println();
        }
    }

    private static void printMenu() {
        System.out.println("""
                ---- Menu ----
                1.  Search (AND, all terms)
                2.  Search (OR, any term)
                3.  Autocomplete suggestions
                4.  Trending (all time)
                5.  Trending (last 24h)
                6.  Zero-result queries
                7.  Documents by category
                8.  Reindex from DB
                9.  Benchmark (cache vs no-cache)
                10. Concurrent search demo
                11. Stats
                0.  Exit
                """);
        System.out.print("Choose: ");
    }

    private static void doSearch(BufferedReader in, SearchController c) throws Exception {
        System.out.print("Query: ");
        String q = in.readLine();
        System.out.print("Top K (default 10): ");
        String kStr = in.readLine().trim();
        int k = kStr.isEmpty() ? 10 : Integer.parseInt(kStr);
        List<SearchResult> results = c.search(q, k, InvertedIndex.MatchMode.AND);
        printResults(results);
    }

    private static void doSearchOr(BufferedReader in, SearchController c) throws Exception {
        System.out.print("Query: ");
        String q = in.readLine();
        System.out.print("Top K (default 10): ");
        String kStr = in.readLine().trim();
        int k = kStr.isEmpty() ? 10 : Integer.parseInt(kStr);
        List<SearchResult> results = c.search(q, k, InvertedIndex.MatchMode.OR);
        printResults(results);
    }

    private static void doAutocomplete(BufferedReader in, AutocompleteController c) throws Exception {
        System.out.print("Prefix: ");
        String prefix = in.readLine();
        System.out.print("K (default 5): ");
        String kStr = in.readLine().trim();
        int k = kStr.isEmpty() ? 5 : Integer.parseInt(kStr);
        List<Suggestion> suggestions = c.suggest(prefix, k);
        System.out.println("Suggestions:");
        for (Suggestion s : suggestions) System.out.println("  " + s);
    }

    private static void printResults(List<SearchResult> results) {
        if (results.isEmpty()) {
            System.out.println("No results.");
            return;
        }
        System.out.println(results.size() + " result(s):");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            System.out.printf("  %2d. %s%n", i + 1, r);
        }
    }

    private static void printTrending(List<QueryStats> stats, String title) {
        System.out.println("== " + title + " ==");
        if (stats.isEmpty()) {
            System.out.println("  (none yet)");
            return;
        }
        for (int i = 0; i < stats.size(); i++) {
            System.out.printf("  %2d. %s%n", i + 1, stats.get(i));
        }
    }

    private static void printStats(SearchService searchService, IndexService indexService,
                                   AutocompleteService autocomplete) {
        System.out.println("== Stats ==");
        System.out.println("  Documents indexed: " + indexService.getIndex().documentCount());
        System.out.println("  Vocabulary size:   " + indexService.getIndex().vocabularySize());
        System.out.println("  Trie terms:        " + autocomplete.size());
        System.out.println("  Total searches:    " + searchService.getSearches());
        System.out.println("  Cache hits:        " + searchService.getCacheHits());
        System.out.println("  Cache misses:      " + searchService.getCacheMisses());
        double hit = searchService.getSearches() == 0 ? 0 :
                (100.0 * searchService.getCacheHits() / searchService.getSearches());
        System.out.printf("  Cache hit rate:    %.1f%%%n", hit);
        System.out.println("  Cache entries:     " + searchService.getCacheSize());
        System.out.println("  History queue:     " + searchService.getHistoryQueueDepth());
        if (searchService.getHistoryDropped() > 0) {
            System.out.println("  History dropped:   " + searchService.getHistoryDropped() + " (queue full)");
        }
    }

    /**
     * Benchmark cache vs no-cache on a generated corpus.
     * Optionally generates up to N synthetic documents by combining the sample corpus.
     */
    private static void runBenchmark(BufferedReader in, SearchService searchService,
                                     DocumentDao documentDao, IndexService indexService,
                                     AutocompleteService autocomplete) throws Exception {
        System.out.print("Generate N synthetic documents (e.g. 10000, 0 to skip): ");
        int n = Integer.parseInt(in.readLine().trim());
        if (n > 0) {
            System.out.println("Generating " + n + " synthetic documents...");
            generateSyntheticDocs(documentDao, n);
            System.out.println("Reindexing...");
            indexService.reindex();
            autocomplete.buildTopKCache();
            System.out.println("Indexed " + indexService.getIndex().documentCount()
                    + " documents, vocabulary " + indexService.getIndex().vocabularySize());
        }
        System.out.print("Queries per scenario (e.g. 200): ");
        int q = Integer.parseInt(in.readLine().trim());

        String[] queries = {
                "java streams", "python data science", "healthy eating",
                "travel paris", "investing stocks", "marathon training",
                "chocolate cake", "spring boot web", "credit score",
                "tokyo sushi"
        };

        // ---- with cache (warm) ----
        searchService.clearCache();
        // first pass warms cache; measure the warm pass
        for (String query : queries) searchService.search(query, 10);
        long startWarm = System.nanoTime();
        for (int i = 0; i < q; i++) {
            String query = queries[i % queries.length];
            searchService.search(query, 10);
        }
        long warmNanos = System.nanoTime() - startWarm;

        // ---- without cache (clear before every query) ----
        long startCold = System.nanoTime();
        for (int i = 0; i < q; i++) {
            String query = queries[i % queries.length];
            searchService.clearCache();
            searchService.search(query, 10);
        }
        long coldNanos = System.nanoTime() - startCold;

        double warmAvgUs = warmNanos / 1000.0 / q;
        double coldAvgUs = coldNanos / 1000.0 / q;
        double speedup = coldAvgUs / warmAvgUs;

        System.out.println();
        System.out.println("== Benchmark (" + q + " queries) ==");
        System.out.printf("  With cache (warm):    %.2f us/query%n", warmAvgUs);
        System.out.printf("  Without cache (cold):  %.2f us/query%n", coldAvgUs);
        System.out.printf("  Speedup:              %.2fx%n", speedup);
        System.out.println("  Cache hits: " + searchService.getCacheHits()
                + " | misses: " + searchService.getCacheMisses());
    }

    /** Generate N documents by recombining the sample corpus with noise suffixes. */
    private static void generateSyntheticDocs(DocumentDao documentDao, int n) throws Exception {
        List<Document> base = SampleData.create();
        Random rnd = new Random(42);
        for (int i = 0; i < n; i++) {
            Document d = base.get(rnd.nextInt(base.size()));
            String title = d.getTitle() + " " + (i + 1);
            String content = d.getContent() + " synthetic doc number " + i;
            documentDao.insert(new Document(title, content, d.getCategory()));
        }
    }

    /**
     * Fire many concurrent searches via a thread pool and measure throughput.
     * Validates thread safety: results should be consistent regardless of parallelism.
     */
    private static void runConcurrentDemo(BufferedReader in, SearchService searchService) throws Exception {
        System.out.print("Number of concurrent searches (e.g. 100): ");
        int total = Integer.parseInt(in.readLine().trim());
        System.out.print("Thread pool size (e.g. 8): ");
        int threads = Integer.parseInt(in.readLine().trim());

        String[] queries = {
                "java", "python", "travel", "investing", "running",
                "cake", "cricket", "sleep", "budget", "tokyo"
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        for (int i = 0; i < total; i++) {
            final String q = queries[i % queries.length];
            pool.submit(() -> {
                searchService.search(q, 10);
                done.incrementAndGet();
            });
        }
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        System.out.println();
        System.out.println("== Concurrent demo ==");
        System.out.println("  Searches completed: " + done.get() + "/" + total);
        System.out.println("  Threads:           " + threads);
        System.out.println("  Elapsed:            " + elapsedMs + " ms");
        System.out.printf("  Throughput:         %.1f searches/sec%n", total * 1000.0 / Math.max(1, elapsedMs));
    }
}
