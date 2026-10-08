package com.yuyanghui.qualityradar.api;

import com.yuyanghui.qualityradar.report.JunitReportService;
import com.yuyanghui.qualityradar.risk.RiskScoringService;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin
public class ApiController {
    private final JdbcTemplate jdbc;
    private final RiskScoringService risk;
    private final JunitReportService reports;
    public ApiController(JdbcTemplate jdbc, RiskScoringService risk, JunitReportService reports) { this.jdbc = jdbc; this.risk = risk; this.reports = reports; }

    @GetMapping("/projects")
    public List<Map<String, Object>> projects() { return jdbc.queryForList("SELECT id, slug, name, created_at FROM projects ORDER BY created_at"); }

    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProject(@RequestBody ProjectRequest request) {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO projects(id,slug,name) VALUES (?,?,?)", id, request.slug(), request.name());
        return Map.of("id", id, "slug", request.slug(), "name", request.name());
    }

    @GetMapping("/projects/{projectId}/dashboard")
    public Map<String, Object> dashboard(@PathVariable UUID projectId) {
        Map<String,Object> latest = jdbc.query("SELECT id,total,passed,failed,errored,skipped,duration_seconds,executed_at FROM test_runs WHERE project_id=? ORDER BY executed_at DESC LIMIT 1", rs -> rs.next() ? Map.of("id",rs.getObject("id"),"total",rs.getInt("total"),"passed",rs.getInt("passed"),"failed",rs.getInt("failed"),"errored",rs.getInt("errored"),"skipped",rs.getInt("skipped"),"durationSeconds",rs.getDouble("duration_seconds"),"executedAt",rs.getObject("executed_at")) : Map.of(), projectId);
        List<Map<String,Object>> trend = jdbc.queryForList("SELECT executed_at AS time, total, passed, failed, errored FROM test_runs WHERE project_id=? ORDER BY executed_at DESC LIMIT 14", projectId);
        List<Map<String,Object>> failures = jdbc.queryForList("SELECT failure_fingerprint AS fingerprint, MAX(failure_type) AS type, COUNT(*) AS occurrences FROM test_results WHERE failure_fingerprint IS NOT NULL AND test_run_id IN (SELECT id FROM test_runs WHERE project_id=?) GROUP BY failure_fingerprint ORDER BY occurrences DESC LIMIT 5", projectId);
        return Map.of("latest", latest, "trend", trend, "topFailures", failures);
    }

    @PostMapping("/projects/{projectId}/test-runs:import")
    public JunitReportService.RunSummary importRun(@PathVariable UUID projectId, @RequestParam("report") MultipartFile report,
                                                    @RequestParam(required=false) String branch, @RequestParam(required=false) String commitSha) throws Exception {
        return reports.importReport(projectId, branch, commitSha, report);
    }

    @GetMapping("/projects/{projectId}/test-runs")
    public List<Map<String,Object>> runs(@PathVariable UUID projectId) { return jdbc.queryForList("SELECT id,branch,commit_sha,executed_at,total,passed,failed,errored,skipped,duration_seconds FROM test_runs WHERE project_id=? ORDER BY executed_at DESC", projectId); }

    @GetMapping("/test-runs/{runId}/test-results")
    public List<Map<String,Object>> results(@PathVariable UUID runId) { return jdbc.queryForList("SELECT suite_name,class_name,test_name,status,duration_seconds,failure_type,failure_message FROM test_results WHERE test_run_id=? ORDER BY suite_name,test_name", runId); }

    @PostMapping("/projects/{projectId}/risk-assessments")
    public Map<String,Object> assess(@PathVariable UUID projectId, @RequestBody DiffRequest request) {
        var a = risk.assess(request.diff()); UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO risk_assessments(id,project_id,score,level,changed_files,additions,deletions,affected_components,factors) VALUES (?,?,?,?,?,?,?,cast(? as jsonb),cast(? as jsonb))", id, projectId,a.score(),a.level(),a.changedFiles().size(),a.additions(),a.deletions(),json(a.affectedComponents()),json(a.factors()));
        return Map.of("id", id, "score", a.score(), "level", a.level(), "additions", a.additions(), "deletions", a.deletions(), "changedFiles", a.changedFiles(), "affectedComponents", a.affectedComponents(), "factors", a.factors(), "recommendations", a.recommendations());
    }

    @PostMapping("/projects/{projectId}/gate-evaluations")
    public Map<String,Object> gate(@PathVariable UUID projectId, @RequestBody GateRequest request) {
        Map<String,Object> run = jdbc.queryForMap("SELECT total,failed,errored,skipped FROM test_runs WHERE id=? AND project_id=?", request.testRunId(),projectId);
        Map<String,Object> assessment = jdbc.queryForMap("SELECT level,affected_components FROM risk_assessments WHERE id=? AND project_id=?", request.riskAssessmentId(),projectId);
        int executable = ((Number)run.get("total")).intValue() - ((Number)run.get("skipped")).intValue();
        int bad = ((Number)run.get("failed")).intValue() + ((Number)run.get("errored")).intValue(); List<String> violations = new ArrayList<>();
        if (((Number)run.get("errored")).intValue() > 0) violations.add("存在执行错误，质量门禁拒绝发布");
        if (executable == 0 || ((double)bad / executable) > 0.05) violations.add("失败率超过 5% 阈值");
        String status = violations.isEmpty() ? "PASSED" : "FAILED"; UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO gate_evaluations(id,project_id,test_run_id,risk_assessment_id,status,violations) VALUES (?,?,?,?,?,cast(? as jsonb))",id,projectId,request.testRunId(),request.riskAssessmentId(),status,json(violations));
        return Map.of("id",id,"status",status,"riskLevel",assessment.get("level"),"violations",violations,"evaluatedAt", Instant.now());
    }
    private String json(Object source) { try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(source); } catch (Exception e) { throw new IllegalStateException(e); } }
    public record ProjectRequest(String slug, String name) {}
    public record DiffRequest(String diff) {}
    public record GateRequest(UUID testRunId, UUID riskAssessmentId) {}
}
