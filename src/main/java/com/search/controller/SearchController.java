package com.search.controller;

import com.search.index.InvertedIndex;
import com.search.model.SearchResult;
import com.search.service.SearchService;

import java.util.List;

/**
 * Controller for search requests. Delegates entirely to {@link SearchService}
 * (controller -> service -> DAO/data-structure). The controller never touches
 * the DAO or the inverted index directly.
 */
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** AND search, top-K results. */
    public List<SearchResult> search(String query, int k) {
        return searchService.search(query, k);
    }

    /** Search with explicit match mode (AND/OR). */
    public List<SearchResult> search(String query, int k, InvertedIndex.MatchMode mode) {
        return searchService.search(query, k, mode);
    }

    /** Default top-10 AND search. */
    public List<SearchResult> search(String query) {
        return searchService.search(query, 10, InvertedIndex.MatchMode.AND);
    }
}
