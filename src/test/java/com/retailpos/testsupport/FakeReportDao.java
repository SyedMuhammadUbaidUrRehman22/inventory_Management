package com.retailpos.testsupport;

import com.retailpos.dao.ReportDao;
import com.retailpos.model.Report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory {@link ReportDao} for unit tests. */
public class FakeReportDao implements ReportDao {

    private final Map<Integer, Report> reports = new TreeMap<>();
    private int nextId = 1;

    @Override
    public int insert(Report report) {
        int id = nextId++;
        report.setReportID(id);
        reports.put(id, report);
        return id;
    }

    @Override
    public Optional<Report> findById(int reportId) {
        return Optional.ofNullable(reports.get(reportId));
    }

    @Override
    public List<Report> findRecent(int limit) {
        List<Report> list = new ArrayList<>(reports.values());
        list.sort(Comparator.comparingInt(Report::getReportID).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
