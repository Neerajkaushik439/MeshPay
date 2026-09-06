package com.meshpay.transactionservice.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public String health() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return "Transaction Service Running and DB OK";
    }
}
