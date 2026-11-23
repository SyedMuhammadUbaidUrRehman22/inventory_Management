package com.retailpos.dao;

import com.retailpos.model.Report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link ReportDao}. */
public class ReportDaoJdbc extends JdbcDao implements ReportDao {

    @Override
    public int insert(Report report) {
        String sql = "INSERT INTO report (report_type, generated_at) VALUES (?, ?)";
        int id = insertReturningKey(sql, ps -> {
            ps.setString(1, report.getReportType());
            ps.setTimestamp(2, Timestamp.valueOf(report.getGeneratedAt()));
        });
        report.setReportID(id);
        return id;
    }

    @Override
    public Optional<Report> findById(int reportId) {
        return queryOne("SELECT report_id, report_type, generated_at FROM report WHERE report_id = ?",
                ps -> ps.setInt(1, reportId), ReportDaoJdbc::map);
    }

    @Override
    public List<Report> findRecent(int limit) {
        return queryList("SELECT report_id, report_type, generated_at FROM report ORDER BY report_id DESC LIMIT ?",
                ps -> ps.setInt(1, Math.max(1, limit)), ReportDaoJdbc::map);
    }

    private static Report map(ResultSet rs) throws SQLException {
        return new Report(
                rs.getInt("report_id"),
                rs.getString("report_type"),
                rs.getTimestamp("generated_at").toLocalDateTime());
    }
}
