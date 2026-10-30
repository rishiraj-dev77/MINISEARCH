package com.search.service;

import com.search.dao.SearchHistoryDao;
import com.search.model.QueryStats;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Thin service over {@link SearchHistoryDao} for trending and analytics reads.
 * Kept separate from {@link SearchService} so the write path (search) and the
 * read path (trending) can evolve independently.
 */
public class TrendingService {

    private final SearchHistoryDao historyDao;

    public TrendingService(SearchHistoryDao historyDao) {
        this.historyDao = historyDao;
    }

    public List<QueryStats> trendingAllTime(int limit) throws SQLException {
        return historyDao.trendingAllTime(limit);
    }

    public List<QueryStats> trendingLast24h(int limit) throws SQLException {
        return historyDao.trendingSince(24, limit);
    }

    public List<QueryStats> trendingSinceHours(int hours, int limit) throws SQLException {
        return historyDao.trendingSince(hours, limit);
    }

    public List<QueryStats> zeroResultQueries(int limit) throws SQLException {
        return historyDao.zeroResultQueries(limit);
    }

    public List<QueryStats> topQueries(int limit) throws SQLException {
        return historyDao.topQueriesFromFrequencyTable(limit);
    }

    public List<QueryStats> categoryCounts() throws SQLException {
        return historyDao.categoryCounts();
    }
}
