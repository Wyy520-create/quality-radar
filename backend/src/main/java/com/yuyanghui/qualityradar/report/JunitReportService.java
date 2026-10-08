package com.yuyanghui.qualityradar.report;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.xml.stream.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JunitReportService {
    private final JdbcTemplate jdbc;
    public JunitReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public RunSummary importReport(UUID projectId, String branch, String commitSha, MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("报告必须是 1~10MB 的 JUnit XML 文件");
        ParsedReport parsed = parse(file.getInputStream());
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO test_runs(id,project_id,branch,commit_sha,executed_at,total,passed,failed,errored,skipped,duration_seconds,report_sha256) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            runId, projectId, branch == null ? "main" : branch, commitSha, Timestamp.from(Instant.now()), parsed.total(), parsed.passed(), parsed.failed(), parsed.errored(), parsed.skipped(), parsed.duration(), sha256(file.getBytes()));
        for (CaseResult item : parsed.cases()) jdbc.update("INSERT INTO test_results(id,test_run_id,suite_name,class_name,test_name,status,duration_seconds,failure_type,failure_message,failure_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), runId, item.suite(), item.className(), item.name(), item.status(), item.duration(), item.failureType(), item.message(), item.fingerprint());
        return new RunSummary(runId, parsed.total(), parsed.passed(), parsed.failed(), parsed.errored(), parsed.skipped(), parsed.duration());
    }

    ParsedReport parse(InputStream source) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        XMLStreamReader reader = factory.createXMLStreamReader(source);
        List<CaseResult> cases = new ArrayList<>(); String suite = "default"; String name = null, clazz = null, status = null, message = null, type = null; double duration = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("testsuite".equals(reader.getLocalName())) suite = attr(reader, "name", "default");
                if ("testcase".equals(reader.getLocalName())) { name = attr(reader, "name", "unnamed"); clazz = attr(reader, "classname", ""); duration = Double.parseDouble(attr(reader, "time", "0")); status = "PASSED"; message = null; type = null; }
                if ("failure".equals(reader.getLocalName()) || "error".equals(reader.getLocalName())) { status = "failure".equals(reader.getLocalName()) ? "FAILED" : "ERRORED"; type = attr(reader, "type", reader.getLocalName()); message = reader.getElementText().strip(); }
                if ("skipped".equals(reader.getLocalName())) status = "SKIPPED";
            } else if (event == XMLStreamConstants.END_ELEMENT && "testcase".equals(reader.getLocalName())) {
                cases.add(new CaseResult(suite, clazz, name, status, duration, type, message, fingerprint(type, message)));
            }
        }
        int passed = (int) cases.stream().filter(c -> c.status().equals("PASSED")).count();
        int failed = (int) cases.stream().filter(c -> c.status().equals("FAILED")).count();
        int errored = (int) cases.stream().filter(c -> c.status().equals("ERRORED")).count();
        int skipped = (int) cases.stream().filter(c -> c.status().equals("SKIPPED")).count();
        return new ParsedReport(cases, cases.size(), passed, failed, errored, skipped, cases.stream().mapToDouble(CaseResult::duration).sum());
    }
    private String attr(XMLStreamReader r, String name, String fallback) { String value = r.getAttributeValue(null, name); return value == null ? fallback : value; }
    private String fingerprint(String type, String message) { return type == null ? null : sha256((type + ":" + (message == null ? "" : message.replaceAll("\\d+", "#"))).getBytes(StandardCharsets.UTF_8)); }
    private String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    record CaseResult(String suite, String className, String name, String status, double duration, String failureType, String message, String fingerprint) {}
    record ParsedReport(List<CaseResult> cases, int total, int passed, int failed, int errored, int skipped, double duration) {}
    public record RunSummary(UUID id, int total, int passed, int failed, int errored, int skipped, double durationSeconds) {}
}
