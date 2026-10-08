package com.yuyanghui.qualityradar.demo;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoDataSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    public DemoDataSeeder(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void run(String... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE slug='shop-demo'", Integer.class);
        if (count != null && count == 0) jdbc.update("INSERT INTO projects(id,slug,name) VALUES (?,?,?)", UUID.fromString("00000000-0000-0000-0000-000000000001"), "shop-demo", "Shop Demo · 电商质量看板");
    }
}
