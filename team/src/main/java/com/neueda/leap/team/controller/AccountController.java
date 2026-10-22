package com.neueda.leap.team.controller;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.service.AccountService;
import com.neueda.leap.team.service.ValueHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Accounts")
public class AccountController {
    private final AccountService service;
    private final ValueHistoryService valueHistory;
    public AccountController(AccountService service, ValueHistoryService valueHistory) {
        this.service = service; this.valueHistory = valueHistory;
    }

    @PostMapping
    @Operation(summary = "Open a USD account for the signed-in CLIENT",
            description = "Account name is optional and may be up to 20 characters. Currency may only be USD. Ownership is taken from the JWT identity.")
    public ResponseEntity<AccountDto> create(@Valid @RequestBody(required = false) CreateAccountRequest request) {
        AccountDto account = service.create(request);
        return ResponseEntity.created(URI.create("/accounts/" + account.accountId())).body(account);
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "CLIENT sees only their accounts. ADMIN may list all or filter by userId.")
    public PageResponse<AccountDto> list(@RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(userId, page, size);
    }
    @GetMapping("/{accountId}")
    public AccountDto get(@PathVariable long accountId) { return service.get(accountId); }
    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Read posted, reserved, and available cash", description = "availableCash = cashBalance - active BUY reservations. All amounts are USD.")
    public AccountBalanceDto balance(@PathVariable long accountId) { return service.balance(accountId); }
    @GetMapping("/{accountId}/positions")
    @Operation(summary = "Read current holdings", description = "Only positive positions. Average price is acquisition cost per unit in USD; it is not a current market quote.")
    public PageResponse<PositionDto> positions(@PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.positions(accountId, page, size);
    }
    @GetMapping("/{accountId}/value-history")
    @Operation(summary = "Read end-of-day account value",
            description = "range is 1D, 1W, 1M or 1Y. One USD point per day up to yesterday: cash plus holdings at that day's close. "
                    + "complete=false when a holding had no price history and was valued at its last execution price.")
    public ValueHistoryDto valueHistory(@PathVariable long accountId, @RequestParam(defaultValue = "1M") String range) {
        return valueHistory.history(accountId, range);
    }
    @GetMapping("/{accountId}/orders")
    @Operation(summary = "Read order history", description = "Newest first, including rejected and cancelled orders. This endpoint does not place orders.")
    public PageResponse<AccountOrderDto> orders(@PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.orders(accountId, page, size);
    }
    @GetMapping("/{accountId}/trades")
    @Operation(summary = "Read successful fills", description = "Newest first, with the stored execution quote and currency conversion used for the trade.")
    public PageResponse<AccountTradeDto> trades(@PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.trades(accountId, page, size);
    }
}
