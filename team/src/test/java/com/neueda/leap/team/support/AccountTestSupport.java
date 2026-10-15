package com.neueda.leap.team.support;

import com.neueda.leap.team.repository.UserRepository;
import com.neueda.leap.team.security.JwtService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public abstract class AccountTestSupport extends PostgresIntegrationTest {
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JsonMapper json;
    @Autowired protected JwtService jwt;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwords;
    protected long owner, other, admin;
    protected String token, otherToken, adminToken;
    private static String hash;

    @BeforeEach
    void seedIdentities() {
        jdbc.execute("TRUNCATE TABLE users, securities RESTART IDENTITY CASCADE");
        if (hash == null) hash = passwords.encode("Account-Test-Password-123!");
        owner = user("owner@example.test", "CLIENT");
        other = user("other@example.test", "CLIENT");
        admin = user("admin@example.test", "ADMIN");
        token = token(owner); otherToken = token(other); adminToken = token(admin);
    }
    @AfterEach
    void deferredFeaturesRemainInactive() {
        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("SELECT count(*) FROM cash_ledger", Long.class).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class).longValue());
    }
    protected long user(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(name,email,password_hash,role_id) VALUES ('Test user',?,?,(SELECT role_id FROM roles WHERE name=?))
                RETURNING user_id
                """, Long.class, email, hash, role);
    }
    protected String token(long id) { return jwt.generateToken(users.findById(id).orElseThrow()).token(); }
    protected JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    protected long account(String bearer) throws Exception {
        return body(mvc.perform(post("/accounts").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isCreated()).andReturn()).path("accountId").asLong();
    }
    protected MvcResult cash(long id, String kind, String amount, UUID requestId, String bearer) throws Exception {
        return mvc.perform(post("/accounts/{id}/{kind}", id, kind).header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "amount", new BigDecimal(amount), "clientRequestId", requestId)))).andReturn();
    }
    protected MvcResult cash(long id, String kind, String amount) throws Exception {
        return cash(id, kind, amount, UUID.randomUUID(), token);
    }
    protected BigDecimal balance(long id) {
        return jdbc.queryForObject("SELECT cash_balance FROM account_cash_balances WHERE account_id=?", BigDecimal.class, id);
    }
    protected long count(String table, long id) {
        // Table names below are test constants, never request input.
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE account_id=?", Long.class, id);
    }
    protected long security() {
        return jdbc.queryForObject("""
                INSERT INTO securities(ticker,name,asset_type,exchange,quote_currency)
                VALUES ('TEST','Test stock','EQUITY','TEST_EXCHANGE','USD') RETURNING security_id
                """, Long.class);
    }
    protected long reserve(long account, long security, String side, String amount) {
        String field = side.equals("B") ? "requested_amount" : "quantity_ordered";
        long order = jdbc.queryForObject("INSERT INTO orders(account_id,security_id,client_request_id,side," + field + """
                ,order_status,accepted_at) VALUES (?,?,?,?,?,'ACCEPTED',clock_timestamp()) RETURNING order_id
                """, Long.class, account, security, UUID.randomUUID(), side, new BigDecimal(amount));
        String reservation = side.equals("B") ? "reserved_cash" : "reserved_quantity";
        jdbc.update("INSERT INTO order_reservations(order_id,account_id,security_id,side," + reservation + ") VALUES (?,?,?,?,?)",
                order, account, security, side, new BigDecimal(amount));
        return order;
    }
    protected JsonNode getJson(String path, String bearer) throws Exception {
        return body(mvc.perform(get(path).header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andReturn());
    }
}
