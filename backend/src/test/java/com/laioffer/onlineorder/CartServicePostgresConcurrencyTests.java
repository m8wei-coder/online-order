package com.laioffer.onlineorder;

import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.OrderItemRepository;
import com.laioffer.onlineorder.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CartServicePostgresConcurrencyTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CartServicePostgresConcurrencyTests {
    private static final String SCHEMA = "baseline_cart_concurrency_test";
    private static final String BASE_URL = System.getenv("TEST_DATABASE_URL");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        if (BASE_URL == null || BASE_URL.isBlank()) {
            throw new IllegalStateException("TEST_DATABASE_URL is required for PostgreSQL concurrency tests");
        }
        createSchema();
        properties.add("spring.datasource.url", () -> BASE_URL + (BASE_URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> "experiment");
        properties.add("spring.datasource.password", () -> "");
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableJdbcRepositories(basePackageClasses = {CartRepository.class, MenuItemRepository.class, OrderItemRepository.class})
    @EnableTransactionManagement
    @EnableCaching
    static class TestApplication {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(BASE_URL + (BASE_URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA, "experiment", "");
        }
        @Bean CartService cartService(CartRepository carts, MenuItemRepository menus, OrderItemRepository items) {
            return new CartService(carts, menus, items);
        }
        @Bean ConcurrentMapCacheManager cacheManager() { return new ConcurrentMapCacheManager("cart"); }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CartService service;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS pause_order_item_write ON order_items");
        jdbc.execute("DROP FUNCTION IF EXISTS pause_order_item_write_fn()");
        jdbc.execute("DROP TABLE IF EXISTS order_items");
        jdbc.execute("DROP TABLE IF EXISTS menu_items");
        jdbc.execute("DROP TABLE IF EXISTS carts");
        jdbc.execute("CREATE TABLE carts(id bigint PRIMARY KEY, customer_id bigint UNIQUE NOT NULL, total_price numeric NOT NULL)");
        jdbc.execute("CREATE TABLE menu_items(id bigint PRIMARY KEY, restaurant_id bigint, name text, description text, price numeric, image_url text)");
        jdbc.execute("CREATE TABLE order_items(id bigserial PRIMARY KEY, menu_item_id bigint NOT NULL, cart_id bigint NOT NULL, price numeric NOT NULL, quantity integer NOT NULL)");
        jdbc.update("INSERT INTO carts VALUES (1, 1, 0)");
        jdbc.update("INSERT INTO menu_items VALUES (2, 1, 'Tea', '', 10, ''), (3, 1, 'Cake', '', 7, '')");
    }

    @Test
    void concurrentAddsToExistingLinePreserveQuantityAndTotal() throws Exception {
        jdbc.update("INSERT INTO carts VALUES (2, 2, 10)");
        jdbc.update("INSERT INTO order_items(menu_item_id,cart_id,price,quantity) VALUES (2,2,10,1)");
        pauseUpdatesForConcurrentOverlap();

        runTogether(() -> service.addMenuItemToCart(2, 2), () -> service.addMenuItemToCart(2, 2));

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM order_items WHERE cart_id=2 AND menu_item_id=2", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT quantity FROM order_items WHERE cart_id=2 AND menu_item_id=2", Integer.class));
        assertEquals(30.0, jdbc.queryForObject("SELECT total_price FROM carts WHERE id=2", Double.class));
    }

    @Test
    void concurrentFirstAddsCreateOneLineWithCombinedQuantityAndPrice() throws Exception {
        pauseInsertsForConcurrentOverlap();
        runTogether(() -> service.addMenuItemToCart(1, 2), () -> service.addMenuItemToCart(1, 2));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM order_items WHERE cart_id=1 AND menu_item_id=2", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT quantity FROM order_items WHERE cart_id=1 AND menu_item_id=2", Integer.class));
        assertEquals(20.0, jdbc.queryForObject("SELECT total_price FROM carts WHERE id=1", Double.class));
    }

    @Test
    void differentItemsInSameCartKeepTheirCombinedTotal() throws Exception {
        runTogether(() -> service.addMenuItemToCart(1, 2), () -> service.addMenuItemToCart(1, 3));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM order_items WHERE cart_id=1", Integer.class));
        assertEquals(17.0, jdbc.queryForObject("SELECT total_price FROM carts WHERE id=1", Double.class));
    }

    @Test
    void clearAndAddLeaveDetailsAndTotalConsistent() throws Exception {
        jdbc.update("INSERT INTO order_items(menu_item_id,cart_id,price,quantity) VALUES (3,1,7,1)");
        jdbc.update("UPDATE carts SET total_price=7 WHERE id=1");
        pauseDeletesForConcurrentOverlap();
        runTogether(() -> service.clearCart(1L), () -> service.addMenuItemToCart(1, 2));
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM order_items WHERE cart_id=1", Integer.class);
        Integer quantity = jdbc.queryForObject("SELECT COALESCE(sum(quantity),0) FROM order_items WHERE cart_id=1", Integer.class);
        Double total = jdbc.queryForObject("SELECT total_price FROM carts WHERE id=1", Double.class);
        assertTrue((rows == 0 && quantity == 0 && total == 0.0) || (rows == 1 && quantity == 1 && total == 10.0),
                "clear/add must commit as either serial order; rows=" + rows + ", quantity=" + quantity + ", total=" + total);
    }

    private void pauseUpdatesForConcurrentOverlap() {
        installDelayTrigger("BEFORE UPDATE ON order_items", "pg_sleep(0.3)");
    }
    private void pauseInsertsForConcurrentOverlap() {
        installDelayTrigger("BEFORE INSERT ON order_items", "pg_sleep(0.3)");
    }
    private void pauseDeletesForConcurrentOverlap() {
        installDelayTrigger("BEFORE DELETE ON order_items", "pg_sleep(0.3)");
    }
    private void installDelayTrigger(String event, String delay) {
        jdbc.execute("CREATE FUNCTION pause_order_item_write_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM " + delay + "; IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF; END $$");
        jdbc.execute("CREATE TRIGGER pause_order_item_write " + event + " FOR EACH ROW EXECUTE FUNCTION pause_order_item_write_fn()");
    }

    private static void runTogether(Runnable firstAction, Runnable secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { ready.countDown(); await(start); firstAction.run(); });
            var second = pool.submit(() -> { ready.countDown(); await(start); secondAction.run(); });
            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not become ready");
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out waiting to start concurrent operation");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void createSchema() {
        try (var connection = DriverManager.getConnection(BASE_URL, "experiment", ""); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare isolated PostgreSQL test schema", e);
        }
    }
}
