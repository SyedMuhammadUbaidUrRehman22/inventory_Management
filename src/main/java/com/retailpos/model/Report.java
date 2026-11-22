package com.retailpos.model;

import java.time.LocalDateTime;

/** Metadata for a generated report. */
public class Report {

    private int reportID;
    private String reportType;
    private LocalDateTime generatedAt;

    public Report() {
    }

    public Report(int reportID, String reportType, LocalDateTime generatedAt) {
        this.reportID = reportID;
        this.reportType = reportType;
        this.generatedAt = generatedAt;
    }

    public int getReportID() {
        return reportID;
    }

    public void setReportID(int reportID) {
        this.reportID = reportID;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public String toString() {
        return "Report{"
                + "reportID=" + reportID
                + ", reportType='" + reportType + '\''
                + ", generatedAt=" + generatedAt
                + '}';
    }
}
