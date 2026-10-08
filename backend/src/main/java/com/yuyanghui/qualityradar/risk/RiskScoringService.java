package com.yuyanghui.qualityradar.risk;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RiskScoringService {
    private static final List<String> SENSITIVE = List.of("auth", "security", "payment", "order", "migration");
    private static final Map<String, String> COMPONENTS = Map.of(
        "payment", "payment", "checkout", "checkout", "order", "checkout",
        "auth", "identity", "user", "identity", "catalog", "catalog", "product", "catalog"
    );

    public Assessment assess(String diff) {
        List<FileChange> files = parse(diff == null ? "" : diff);
        int additions = files.stream().mapToInt(FileChange::additions).sum();
        int deletions = files.stream().mapToInt(FileChange::deletions).sum();
        List<Factor> factors = new ArrayList<>();
        int score = 0;
        int production = (int) files.stream().filter(f -> !f.path().contains("/test/") && !f.path().startsWith("tests/")).count();
        if (production > 0) {
            int points = Math.min(production * 8, 32);
            score += points; factors.add(new Factor("生产代码变更", points, production + " 个文件"));
        }
        int sensitive = (int) files.stream().filter(f -> SENSITIVE.stream().anyMatch(s -> f.path().toLowerCase().contains(s))).count();
        if (sensitive > 0) {
            int points = Math.min(sensitive * 15, 30);
            score += points; factors.add(new Factor("敏感模块变更", points, sensitive + " 个文件"));
        }
        int config = (int) files.stream().filter(f -> f.path().matches(".*(pom.xml|package.json|application.*|docker-compose.*|migration.*)")).count();
        if (config > 0) {
            int points = Math.min(config * 12, 24);
            score += points; factors.add(new Factor("构建或数据配置变更", points, config + " 个文件"));
        }
        int lines = additions + deletions;
        if (lines >= 300) { score += 16; factors.add(new Factor("大规模改动", 16, lines + " 行")); }
        else if (lines >= 100) { score += 8; factors.add(new Factor("中等规模改动", 8, lines + " 行")); }
        Set<String> components = new TreeSet<>();
        for (FileChange f : files) for (var e : COMPONENTS.entrySet()) if (f.path().toLowerCase().contains(e.getKey())) components.add(e.getValue());
        List<Recommendation> recommendations = recommend(components);
        if (!components.isEmpty() && recommendations.isEmpty()) { score += 15; factors.add(new Factor("缺少映射回归用例", 15, String.join("、", components))); }
        score = Math.min(score, 100);
        String level = score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW";
        return new Assessment(score, level, additions, deletions, files, List.copyOf(components), factors, recommendations);
    }

    private List<FileChange> parse(String diff) {
        List<FileChange> result = new ArrayList<>(); String current = null; int add = 0, del = 0;
        for (String line : diff.lines().toList()) {
            if (line.startsWith("+++ b/")) {
                if (current != null) result.add(new FileChange(current, add, del));
                current = line.substring(6); add = 0; del = 0;
            } else if (current != null && line.startsWith("+") && !line.startsWith("+++")) add++;
            else if (current != null && line.startsWith("-") && !line.startsWith("---")) del++;
        }
        if (current != null) result.add(new FileChange(current, add, del));
        return result;
    }

    private List<Recommendation> recommend(Set<String> components) {
        Map<String, Recommendation> catalog = Map.of(
            "identity", new Recommendation("identity-contract", "身份与鉴权契约回归", "P0", 45, List.of("identity")),
            "checkout", new Recommendation("checkout-e2e", "下单与库存端到端回归", "P0", 90, List.of("checkout")),
            "payment", new Recommendation("payment-contract", "支付状态契约回归", "P0", 60, List.of("payment")),
            "catalog", new Recommendation("catalog-api", "商品目录接口回归", "P1", 30, List.of("catalog"))
        );
        return components.stream().map(catalog::get).filter(Objects::nonNull).sorted(Comparator.comparing(Recommendation::priority).thenComparing(Recommendation::testKey)).toList();
    }

    public record FileChange(String path, int additions, int deletions) {}
    public record Factor(String rule, int points, String detail) {}
    public record Recommendation(String testKey, String displayName, String priority, int estimatedSeconds, List<String> covers) {}
    public record Assessment(int score, String level, int additions, int deletions, List<FileChange> changedFiles,
                             List<String> affectedComponents, List<Factor> factors, List<Recommendation> recommendations) {}
}
