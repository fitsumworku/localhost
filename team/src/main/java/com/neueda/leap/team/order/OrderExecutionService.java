package com.neueda.leap.team.order;

import com.neueda.leap.team.config.OrderProperties;
import com.neueda.leap.team.market.QuoteResult;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.neueda.leap.team.order.OrderRepository.ts;

/** Short database transactions only. Network calls belong to OrderWorker, outside these transactions. */
@Service
public class OrderExecutionService {
    private static final BigDecimal MAX_CASH=new BigDecimal("999999999999999999.99");
    private static final BigDecimal QUANTITY_LIMIT=new BigDecimal("10000000000000000");
    private final OrderRepository repo;
    private final OrderProperties properties;
    private final ExecutionPricing pricing;
    private final Clock clock;
    public OrderExecutionService(OrderRepository repo,OrderProperties properties,ExecutionPricing pricing,Clock clock) {
        this.repo=repo;this.properties=properties;this.pricing=pricing;this.clock=clock;
    }
    public record Claim(long orderId,UUID token,SecurityDto security) {}

    @Transactional
    public Optional<Claim> claim(long id) {
        var found=repo.find(id);if(found.isEmpty())return Optional.empty();
        OrderRow before=found.get();
        // Identical order to deposits, withdrawals, order submission and cancellation.
        // Accepted commitments continue after logout/blacklisting; those block new client actions.
        repo.lockOwner(before.ownerId());repo.lockBalance(before.accountId());
        OrderRow o=repo.locked(id);Instant now=now(o);
        if(o.status().equals("IN_EXECUTION")) {
            if(o.leaseUntil()!=null&&o.leaseUntil().isAfter(now))return Optional.empty();
            failedAttempt(o,"FAILED","WORKER_LEASE_EXPIRED",now);
        } else if(!o.status().equals("ACCEPTED")||o.nextAttemptAt()==null||o.nextAttemptAt().isAfter(now))return Optional.empty();
        UUID token=UUID.randomUUID();
        repo.update("""
                UPDATE orders SET order_status='IN_EXECUTION',updated_date=?,execution_token=?,execution_lease_until=?,
                    next_attempt_at=NULL,execution_attempts=execution_attempts+1,last_execution_message=NULL WHERE order_id=?
                """,ts(now),token,ts(now.plusSeconds(properties.leaseSeconds())),id);
        return Optional.of(new Claim(id,token,o.security()));
    }

    @Transactional
    public void complete(Claim claim,Map<String,QuoteResult> quotes) {
        var found=repo.find(claim.orderId());if(found.isEmpty())return;
        OrderRow hint=found.get();repo.lockOwner(hint.ownerId());BigDecimal balance=repo.lockBalance(hint.accountId());
        OrderRow o=repo.locked(claim.orderId());Instant now=now(o);
        // Old/replayed workers cannot post. Expired leases are recovered by the next claim.
        if(!o.status().equals("IN_EXECUTION")||!claim.token().equals(o.executionToken())||o.leaseUntil()==null||!o.leaseUntil().isAfter(now))return;
        SecurityDto security=repo.security(o.securityId(),true);
        var decision=pricing.evaluate(security,quotes,now);
        switch(decision.kind()) {
            case "WAIT" -> requeue(o,decision.reason(),decision.retrySeconds(),now,false);
            case "RETRY" -> requeue(o,decision.reason(),decision.retrySeconds(),now,true);
            case "REJECT" -> reject(o,decision.reason(),now);
            case "READY" -> fill(o,balance,decision.price(),now);
            default -> throw new IllegalStateException("Unknown pricing result");
        }
    }

    private void fill(OrderRow o,BigDecimal balance,ExecutionPricing.Price p,Instant now) {
        var hold=repo.details(o).reservation();
        if(hold==null||!hold.status().equals("ACTIVE"))throw new IllegalStateException("Execution requires an active reservation");
        boolean buy=o.side().equals("B");
        if(buy?hold.reservedCash().compareTo(o.amount())!=0:hold.reservedQuantity().compareTo(o.quantity())!=0)
            throw new IllegalStateException("Reservation differs from accepted order");
        // Always round BUY units DOWN so a price move cannot spend more than reserved USD.
        BigDecimal quantity=buy?o.amount().divide(p.usdPrice(),12,RoundingMode.DOWN):o.quantity();
        BigDecimal cash=quantity.multiply(p.usdPrice()).setScale(2,RoundingMode.HALF_UP);
        if(quantity.signum()<=0||quantity.compareTo(QUANTITY_LIMIT)>=0||cash.signum()<=0||cash.compareTo(MAX_CASH)>0) {
            reject(o,"ORDER_VALUE_OUT_OF_RANGE",now);return;
        }
        var position=repo.position(o.accountId(),o.securityId());
        if(buy && (cash.compareTo(o.amount())>0 || cash.compareTo(balance.subtract(repo.reservedCash(o.accountId())).add(hold.reservedCash()))>0)) {
            reject(o,"INSUFFICIENT_AVAILABLE_CASH",now);return;
        }
        if(!buy && quantity.compareTo(position.quantity().subtract(repo.reservedQuantity(o.accountId(),o.securityId())).add(hold.reservedQuantity()))>0) {
            reject(o,"INSUFFICIENT_AVAILABLE_QUANTITY",now);return;
        }
        BigDecimal newBalance=buy?balance.subtract(cash):balance.add(cash);
        BigDecimal newQuantity=buy?position.quantity().add(quantity):position.quantity().subtract(quantity);
        if(newBalance.compareTo(MAX_CASH)>0||newQuantity.compareTo(QUANTITY_LIMIT)>=0) {reject(o,"ACCOUNT_LIMIT_EXCEEDED",now);return;}
        BigDecimal average=newQuantity.signum()==0?BigDecimal.ZERO:buy?
                position.quantity().multiply(position.average()).add(cash).divide(newQuantity,12,RoundingMode.HALF_UP):position.average();
        if(average.compareTo(QUANTITY_LIMIT)>=0) {reject(o,"POSITION_COST_OUT_OF_RANGE",now);return;}
        long execution=repo.insert("""
                INSERT INTO executions(order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,
                    quote_price,quote_currency,quote_timestamp,quote_source,fx_rate_to_usd,fx_quote_timestamp,fx_source,
                    quantity_filled,price_of_execution)
                VALUES (?,?,?,?,'FILLED',?,?,?,?,?,?,?,?,?,?,?) RETURNING execution_id
                """,o.id(),o.accountId(),o.securityId(),o.side(),ts(o.updatedAt()),ts(now),p.quotePrice(),p.quote().currency(),
                ts(p.quote().asOf()),p.quote().source(),p.fxRate(),p.fxQuote()==null?null:ts(p.fxQuote().asOf()),
                p.fxQuote()==null?null:p.fxQuote().source(),quantity,p.usdPrice());
        repo.update("INSERT INTO trades(execution_id,account_id,trade_date,settlement_date) VALUES (?,?,?,?)",
                execution,o.accountId(),ts(now),ts(now));
        repo.update("""
                INSERT INTO account_positions(account_id,security_id,quantity,average_price,updated_date) VALUES (?,?,?,?,?)
                ON CONFLICT (account_id,security_id) DO UPDATE SET quantity=EXCLUDED.quantity,
                    average_price=EXCLUDED.average_price,updated_date=EXCLUDED.updated_date
                """,o.accountId(),o.securityId(),newQuantity,average,ts(now));
        repo.update("UPDATE account_cash_balances SET cash_balance=?,updated_date=? WHERE account_id=?",newBalance,ts(now),o.accountId());
        resolveHold(o,"CONSUMED",now);
        terminal(o,"FILLED",null,now);
    }
    private void requeue(OrderRow o,String reason,long delay,Instant now,boolean failed) {
        if(failed)failedAttempt(o,"FAILED",reason,now);
        repo.update("""
                UPDATE orders SET order_status='ACCEPTED',updated_date=?,execution_token=NULL,execution_lease_until=NULL,
                    next_attempt_at=?,last_execution_message=? WHERE order_id=?
                """,ts(now),ts(now.plusSeconds(delay)),reason,o.id());
    }
    private void reject(OrderRow o,String reason,Instant now) {
        failedAttempt(o,"REJECTED",reason,now);resolveHold(o,"RELEASED",now);terminal(o,"REJECTED",reason,now);
    }
    private void terminal(OrderRow o,String state,String reason,Instant now) {
        repo.update("""
                UPDATE orders SET order_status=?,updated_date=?,terminal_at=?,rejection_reason=?,next_attempt_at=NULL,
                    execution_token=NULL,execution_lease_until=NULL,last_execution_message=? WHERE order_id=?
                """,state,ts(now),ts(now),reason,reason,o.id());
    }
    private void resolveHold(OrderRow o,String state,Instant now) {
        int changed=repo.update("UPDATE order_reservations SET status=?,resolved_at=? WHERE order_id=? AND status='ACTIVE'",state,ts(now),o.id());
        if(changed!=1)throw new IllegalStateException("Order reservation is missing");
    }
    private void failedAttempt(OrderRow o,String state,String reason,Instant now) {
        repo.update("""
                INSERT INTO executions(order_id,account_id,security_id,side,status_of_execution,started_at,finished_at,failure_reason)
                VALUES (?,?,?,?,?,?,?,?)
                """,o.id(),o.accountId(),o.securityId(),o.side(),state,ts(o.updatedAt()),ts(now),reason);
    }
    private Instant now(OrderRow o) { Instant value=clock.instant();return value.isBefore(o.updatedAt())?o.updatedAt():value; }
}
