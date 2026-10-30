package com.laioffer.onlineorder;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.UserDetailsManager;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorityQueryPostgresTests {
    @Test
    void loadUserByUsernameReturnsAuthorityTextFromPostgres() throws Exception {
        String baseUrl = System.getenv("TEST_DATABASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalStateException("TEST_DATABASE_URL is required");
        String schema = "baseline_authority_test";
        String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "experiment", "");
        try (Connection c = new DriverManagerDataSource(baseUrl, "experiment", "").getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        }
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS customers (email text primary key, password text not null, enabled boolean not null)");
            s.execute("CREATE TABLE IF NOT EXISTS authorities (id serial primary key, email text not null, authority text not null)");
            s.execute("DELETE FROM authorities WHERE email='fixture@example.test'");
            s.execute("DELETE FROM customers WHERE email='fixture@example.test'");
            s.execute("INSERT INTO customers(email,password,enabled) VALUES ('fixture@example.test','{noop}secret',true)");
            s.execute("INSERT INTO authorities(email,authority) VALUES ('fixture@example.test','ROLE_USER')");
        }
        UserDetailsManager manager = new AppConfig().users(ds);
        UserDetails loaded = manager.loadUserByUsername("fixture@example.test");
        assertEquals("ROLE_USER", loaded.getAuthorities().iterator().next().getAuthority());
    }
}
