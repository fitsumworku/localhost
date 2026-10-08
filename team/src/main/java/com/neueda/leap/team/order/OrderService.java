package com.neueda.leap.team.order;

import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.service.*;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.neueda.leap.team.order.OrderRepository.ts;

@Service
@PreAuthorize("hasAnyRole('CLIENT','ADMIN')")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class OrderService {
    private final OrderRepository repo;
    private final AccountAccessService access;
    private final Clock clock;
    public OrderService(OrderRepository repo, AccountAccessService access, Clock clock) {
        this.repo=repo;this.access=access;this.clock=clock;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public OrderOperationResponse place(long account, PlaceOrderRequest request) {
        validate(request);
        var user=access.lockActiveClient();
        access.owned(account,user.getId());
        BigDecimal cash=repo.lockBalance(account);
        var existing=repo.findRequest(account,request.clientRequestId());
        if(existing.isPresent()) {
            OrderRow prior=existing.get();
            if(prior.securityId()!=request.securityId() || !prior.side().equals(request.side().name())
                    || !same(prior.amount(),request.requestedAmount()) || !same(prior.quantity(),request.quantityOrdered()))
                throw new ApiException(HttpStatus.CONFLICT,"REQUEST_ID_REUSED","This clientRequestId was used for a different order.");
            return new OrderOperationResponse(repo.details(prior),true);
        }
        var security=repo.security(request.securityId(),true);
        Instant now=clock.instant();
        long id=repo.insert("""
                INSERT INTO orders(account_id,security_id,client_request_id,side,requested_amount,quantity_ordered,
                    created_date,updated_date,next_attempt_at) VALUES (?,?,?,?,?,?,?,?,?) RETURNING order_id
                """,account,request.securityId(),request.clientRequestId(),request.side().name(),request.requestedAmount(),
                request.quantityOrdered(),ts(now),ts(now),ts(now));
        String reason=null;
        if(!security.status().equals("ACTIVE"))reason="SECURITY_NOT_TRADABLE";
        else if(request.side()==PlaceOrderRequest.Side.B && request.requestedAmount().compareTo(cash.subtract(repo.reservedCash(account)))>0)
            reason="INSUFFICIENT_AVAILABLE_CASH";
        else if(request.side()==PlaceOrderRequest.Side.S && request.quantityOrdered().compareTo(
                repo.position(account,security.securityId()).quantity().subtract(repo.reservedQuantity(account,security.securityId())))>0)
            reason="INSUFFICIENT_AVAILABLE_QUANTITY";
        if(reason!=null) {
            repo.update("UPDATE orders SET order_status='REJECTED',terminal_at=?,rejection_reason=?,next_attempt_at=NULL WHERE order_id=?",ts(now),reason,id);
        } else {
            repo.update("UPDATE orders SET order_status='ACCEPTED',accepted_at=? WHERE order_id=?",ts(now),id);
            repo.update("""
                    INSERT INTO order_reservations(order_id,account_id,security_id,side,reserved_cash,reserved_quantity,created_date)
                    VALUES (?,?,?,?,?,?,?)
                    """,id,account,security.securityId(),request.side().name(),request.requestedAmount(),request.quantityOrdered(),ts(now));
        }
        // Return normally even for business rejection, so the order commits before HTTP 409.
        return new OrderOperationResponse(repo.details(repo.find(id).orElseThrow()),false);
    }

    public OrderDetailsDto get(long account,long id) { return repo.details(readable(account,id)); }
    public PageResponse<ExecutionDto> executions(long account,long id,int page,int size) {
        readable(account,id);return repo.executions(id,page,size);
    }
    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public OrderDetailsDto cancel(long account,long id) {
        var user=access.lockActiveClient();access.owned(account,user.getId());repo.lockBalance(account);
        OrderRow o=repo.locked(id);
        if(o.accountId()!=account)throw OrderRepository.notFound();
        if(o.status().equals("CANCELLED"))return repo.details(o);
        if(!o.status().equals("ACCEPTED"))throw new ApiException(HttpStatus.CONFLICT,"ORDER_NOT_CANCELLABLE","Only an accepted order waiting for execution can be cancelled.");
        Instant now=clock.instant().isBefore(o.updatedAt())?o.updatedAt():clock.instant();
        int released=repo.update("UPDATE order_reservations SET status='RELEASED',resolved_at=? WHERE order_id=? AND status='ACTIVE'",ts(now),id);
        if(released!=1)throw new IllegalStateException("Accepted order is missing its reservation");
        repo.update("UPDATE orders SET order_status='CANCELLED',terminal_at=?,updated_date=?,next_attempt_at=NULL,last_execution_message=NULL WHERE order_id=?",ts(now),ts(now),id);
        return repo.details(repo.find(id).orElseThrow());
    }
    private OrderRow readable(long account,long id) {
        access.readable(account);OrderRow o=repo.find(id).orElseThrow(OrderRepository::notFound);
        if(o.accountId()!=account)throw OrderRepository.notFound();return o;
    }
    private static boolean same(BigDecimal a,BigDecimal b) { return a==null?b==null:b!=null&&a.compareTo(b)==0; }
    private static void validate(PlaceOrderRequest r) {
        if(r==null||r.clientRequestId()==null||r.securityId()<=0||r.side()==null
                ||(r.side()==PlaceOrderRequest.Side.B?(!valid(r.requestedAmount(),18,2)||r.quantityOrdered()!=null)
                    :(!valid(r.quantityOrdered(),16,12)||r.requestedAmount()!=null)))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ORDER","BUY requires only requestedAmount in USD (2 decimals); SELL requires only quantityOrdered (12 decimals).");
    }
    private static boolean valid(BigDecimal n,int integer,int fraction) {
        return n!=null&&n.signum()>0&&n.scale()<=fraction&&n.precision()-n.scale()<=integer;
    }
}
