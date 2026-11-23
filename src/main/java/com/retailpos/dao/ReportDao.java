package com.retailpos.dao;

import com.retailpos.model.Report;

import java.util.List;
import java.util.Optional;

/** Data access for report generation logs. */
public interface ReportDao {

    /** Inserts a new report generation entry and returns the generated report_id. */
    int insert(Report report);

    /** Finds a report entry by ID. */
    Optional<Report> findById(int reportId);

    /** Returns the most recent reports generated. */
    List<Report> findRecent(int limit);
}
