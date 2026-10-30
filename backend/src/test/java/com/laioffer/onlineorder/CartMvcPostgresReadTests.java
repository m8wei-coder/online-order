package com.laioffer.onlineorder;

import com.laioffer.onlineorder.controller.CartController;
import com.laioffer.onlineorder.repository.CartRepository;
import com.laioffer.onlineorder.repository.CustomerRepository;
import com.laioffer.onlineorder.repository.MenuItemRepository;
import com.laioffer.onlineorder.repository.OrderItemRepository;
import com.laioffer.onlineorder.service.CartService;
import com.laioffer.onlineorder.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CartMvcPostgresReadTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CartMvcPostgresReadTests {
    private static final String SCHEMA = "m1_cart_mvc_read_test";
    private static final String BASE_URL = System.getenv("TEST_DATABASE_URL");

    @Configuration
    @EnableAutoConfiguration
    @EnableJdbcRepositories(basePackageClasses = {CartRepository.class, CustomerRepository.class, MenuItemRepository.class, OrderItemRepository.class})
    @Import({CartController.class, CartService.class, CustomerService.class})
    static class TestApplication {
        @Bean CountingDataSource dataSource() {
            return new CountingDataSource(new DriverManagerDataSource(schemaUrl(), "experiment", ""));
        }
        @Bean org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
            return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
        @Bean org.springframework.security.provisioning.UserDetailsManager userDetailsManager(DataSource dataSource) {
            return new org.springframework.security.provisioning.JdbcUserDetailsManager(dataSource);
        }
        @Bean org.springframework.security.web.SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .addFilterBefore(new TestPrincipalFilter(), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
            return http.build();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private CountingDataSource dataSource;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("DROP TABLE IF EXISTS order_items");
        jdbc.execute("DROP TABLE IF EXISTS carts");
        jdbc.execute("DROP TABLE IF EXISTS menu_items");
        jdbc.execute("DROP TABLE IF EXISTS customers");
        jdbc.execute("DROP TABLE IF EXISTS authorities");
        jdbc.execute("CREATE TABLE customers(id bigint PRIMARY KEY, email text UNIQUE NOT NULL, password text NOT NULL, enabled boolean NOT NULL, first_name text, last_name text)");
        jdbc.execute("CREATE TABLE authorities(id bigserial PRIMARY KEY, email text NOT NULL, authority text NOT NULL)");
        jdbc.execute("CREATE TABLE carts(id bigint PRIMARY KEY, customer_id bigint UNIQUE NOT NULL, total_price numeric NOT NULL)");
        jdbc.execute("CREATE TABLE menu_items(id bigint PRIMARY KEY, restaurant_id bigint, name text, description text, price numeric, image_url text)");
        jdbc.execute("CREATE TABLE order_items(id bigserial PRIMARY KEY, menu_item_id bigint NOT NULL, cart_id bigint NOT NULL, price numeric NOT NULL, quantity integer NOT NULL)");
        jdbc.update("INSERT INTO customers VALUES (1,'one@example.test','{noop}secret',true,'One','User'), (2,'two@example.test','{noop}secret',true,'Two','User')");
        jdbc.update("INSERT INTO carts VALUES (11,1,0), (22,2,0)");
        for (int i = 1; i <= 12; i++) {
            jdbc.update("INSERT INTO menu_items VALUES (?, ?, ?, ?, ?, ?)", 100 + i, i < 3 ? 7 : 8, "Menu " + i, "Description " + i, i * 4.25, "image-" + i);
            jdbc.update("INSERT INTO order_items(menu_item_id,cart_id,price,quantity) VALUES (?,11,?,?)", 100 + i, i == 1 ? 3.75 : i * 4.25, i == 1 ? 2 : 1);
        }
        jdbc.update("INSERT INTO order_items(menu_item_id,cart_id,price,quantity) VALUES (103,22,11.00,1)");
        jdbc.update("UPDATE carts SET total_price=280.5 WHERE id=11");
        dataSource.reset();
    }

    @Test
    void fullCartRoutesStayWithinThreeSelectsAndKeepJsonContractForAllReadShapes() throws Exception {
        dataSource.reset();
        readAndAssert("one@example.test", "$.order_items.length()", 12);
        assertWithinBound("12-item first GET /cart");
        dataSource.reset();
        assertEquals(200, mvc.perform(get("/cart").with(authenticated("one@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.total_price").value(280.5))
                .andExpect(jsonPath("$.order_items[0].order_item_id").exists())
                .andExpect(jsonPath("$.order_items[0].menu_item_id").exists())
                .andExpect(jsonPath("$.order_items[0].restaurant_id").exists())
                .andExpect(jsonPath("$.order_items[0].price").exists())
                .andExpect(jsonPath("$.order_items[0].quantity").exists())
                .andExpect(jsonPath("$.order_items[0].menu_item_name").exists())
                .andExpect(jsonPath("$.order_items[0].menu_item_description").exists())
                .andExpect(jsonPath("$.order_items[0].menu_item_image_url").exists())
                .andReturn().getResponse().getStatus());
        assertWithinBound("12-item repeated GET /cart");

        dataSource.reset();
        mvc.perform(get("/cart").with(authenticated("two@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(22));
        assertWithinBound("alternating second-user GET /cart");
        dataSource.reset();
        mvc.perform(get("/cart?customer_id=2").with(authenticated("one@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(11));
        assertWithinBound("alternating first-user GET /cart");

        jdbc.update("DELETE FROM order_items WHERE cart_id=11");
        jdbc.update("UPDATE carts SET total_price=0 WHERE id=11");
        dataSource.reset();
        mvc.perform(get("/cart").with(authenticated("one@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_items.length()").value(0));
        assertWithinBound("empty GET /cart");

        jdbc.update("INSERT INTO order_items(menu_item_id,cart_id,price,quantity) VALUES (101,11,3.75,2)");
        jdbc.update("UPDATE carts SET total_price=284.75 WHERE id=11");
        dataSource.reset();
        mvc.perform(get("/cart").with(authenticated("one@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_items.length()").value(1))
                .andExpect(jsonPath("$.order_items[0].price").value(3.75));
        assertWithinBound("single-item GET /cart");
    }

    @Test
    void principalSelectsIsolatedCustomerAndWritesAreVisibleOnNextRead() throws Exception {
        dataSource.reset();
        mvc.perform(get("/cart").with(authenticated("two@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(22))
                .andExpect(jsonPath("$.order_items.length()").value(1))
                .andExpect(jsonPath("$.order_items[0].price").value(11.0));
        assertWithinBound("second user's GET /cart");

        dataSource.reset();
        mvc.perform(post("/cart").with(authenticated("one@example.test")).contentType("application/json").content("{\"menu_id\":101}"))
                .andExpect(status().isOk());
        dataSource.reset();
        mvc.perform(get("/cart").with(authenticated("one@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_items[0].quantity").value(3));
        assertWithinBound("post-write GET /cart");

        mvc.perform(post("/cart/checkout").with(authenticated("one@example.test")))
                .andExpect(status().isOk());
        dataSource.reset();
        mvc.perform(get("/cart").with(authenticated("one@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_items.length()").value(0))
                .andExpect(jsonPath("$.total_price").value(0.0));
        assertWithinBound("post-checkout GET /cart");
    }

    private void readAndAssert(String username, String path, int expected) throws Exception {
        mvc.perform(get("/cart").with(authenticated(username)))
                .andExpect(status().isOk()).andExpect(jsonPath(path).value(expected))
                .andExpect(jsonPath("$.order_items[0].price").value(3.75))
                .andExpect(jsonPath("$.order_items[0].menu_item_description").value("Description 1"));
    }

    private void assertWithinBound(String label) {
        System.out.println("M1_SQL_EVIDENCE " + label + " count=" + dataSource.selectCount() + " statements=" + dataSource.selectSql());
        assertTrue(dataSource.selectCount() <= 3, label + " exceeded SQL bound: " + dataSource.selectSql());
    }

    private static RequestPostProcessor authenticated(String username) {
        return request -> { request.addHeader("X-Test-Principal", username); return request; };
    }

    private static String schemaUrl() {
        return BASE_URL + (BASE_URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
    }

    private static void createSchema() {
        try (Connection connection = DriverManager.getConnection(BASE_URL, "experiment", ""); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare isolated PostgreSQL test schema", e);
        }
    }

    static {
        if (BASE_URL == null || BASE_URL.isBlank()) throw new IllegalStateException("TEST_DATABASE_URL is required");
        createSchema();
    }

    static final class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger selects = new AtomicInteger();
        private final List<String> sql = new ArrayList<>();
        CountingDataSource(DataSource target) { super(target); }
        @Override public Connection getConnection() throws java.sql.SQLException { return count(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws java.sql.SQLException { return count(super.getConnection(user, password)); }
        private Connection count(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                try {
                    Object result = method.invoke(connection, args);
                    if ((method.getName().equals("prepareStatement") || method.getName().equals("prepareCall")) && args != null && args.length > 0 && args[0] instanceof String statement && statement.stripLeading().toLowerCase().startsWith("select")) {
                        selects.incrementAndGet();
                        synchronized (sql) { sql.add(statement.replaceAll("\\s+", " ").trim()); }
                    }
                    return result;
                } catch (InvocationTargetException e) { throw e.getCause(); }
            });
        }
        void reset() { selects.set(0); synchronized (sql) { sql.clear(); } }
        int selectCount() { return selects.get(); }
        List<String> selectSql() { synchronized (sql) { return List.copyOf(sql); } }
    }

    static final class TestPrincipalFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            String username = request.getHeader("X-Test-Principal");
            if (username != null) {
                var principal = org.springframework.security.core.userdetails.User.withUsername(username).password("secret").roles("USER").build();
                var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, "secret", principal.getAuthorities());
                org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            }
            try { chain.doFilter(request, response); }
            finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        }
    }
}
