package com.neueda.leap.team.controller;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.enums.CashTransactionStatus;
import com.neueda.leap.team.service.AccountCashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/{accountId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account cash")
public class AccountCashController {
    private final AccountCashService service;
    public AccountCashController(AccountCashService service) { this.service = service; }

    @PostMapping("/deposits")
    @Operation(summary = "Simulate a deposit into an owned account", description = "Reuse clientRequestId on a retry. New success: 201; replayed success: 200; recorded business failure: 409.")
    public ResponseEntity<CashOperationResponse> deposit(@PathVariable long accountId, @Valid @RequestBody CashMovementRequest request) {
        return response(service.deposit(accountId, request));
    }
    @PostMapping("/withdrawals")
    @Operation(summary = "Simulate a withdrawal from available cash", description = "Reserved cash cannot be withdrawn. A failed attempt is retained and returns 409. No banking integration.")
    public ResponseEntity<CashOperationResponse> withdraw(@PathVariable long accountId, @Valid @RequestBody CashMovementRequest request) {
        return response(service.withdraw(accountId, request));
    }
    @GetMapping("/transactions")
    public PageResponse<CashTransactionDto> transactions(@PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.transactions(accountId, page, size);
    }
    @GetMapping("/transactions/{transactionId}")
    public CashTransactionDto transaction(@PathVariable long accountId, @PathVariable long transactionId) {
        return service.transaction(accountId, transactionId);
    }
    @GetMapping("/ledger")
    @Operation(summary = "Read posted cash movements", description = "Newest posting first; includes trades and non-trading cash movements. Holds and failed transactions do not create ledger entries.")
    public PageResponse<LedgerEntryDto> ledger(@PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.ledger(accountId, page, size);
    }

    private ResponseEntity<CashOperationResponse> response(CashOperationResponse result) {
        HttpStatus status = result.transaction().status() == CashTransactionStatus.FAILED ? HttpStatus.CONFLICT
                : result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        URI location = URI.create("/accounts/" + result.transaction().accountId() + "/transactions/" + result.transaction().transactionId());
        return ResponseEntity.status(status).location(location).body(result);
    }
}
