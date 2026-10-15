package com.neueda.leap.team.order;

import com.neueda.leap.team.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/{accountId}/orders")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service){this.service=service;}
    @PostMapping
    @Operation(summary="Place a market order",description="CLIENT owner only. B: requestedAmount in USD. S: quantityOrdered. clientRequestId provides idempotency. Acceptance reserves cash or units; execution happens independently.")
    public ResponseEntity<OrderOperationResponse> place(@PathVariable long accountId,@Valid @RequestBody PlaceOrderRequest request) {
        var result=service.place(accountId,request);
        var status=result.details().order().orderStatus().equals("REJECTED")?HttpStatus.CONFLICT:
                result.replayed()?HttpStatus.OK:HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).header(HttpHeaders.LOCATION,"/accounts/"+accountId+"/orders/"+result.details().order().orderId()).body(result);
    }
    @GetMapping("/{orderId}")
    public OrderDetailsDto get(@PathVariable long accountId,@PathVariable long orderId){return service.get(accountId,orderId);}
    @PostMapping("/{orderId}/cancel")
    @Operation(summary="Cancel a waiting order and release its reservation",description="CLIENT owner only. Returns 409 once execution has started or the order is terminal. Repeated cancellation is safe.")
    public OrderDetailsDto cancel(@PathVariable long accountId,@PathVariable long orderId){return service.cancel(accountId,orderId);}
    @GetMapping("/{orderId}/executions")
    public PageResponse<ExecutionDto> executions(@PathVariable long accountId,@PathVariable long orderId,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.executions(accountId,orderId,page,size);}
}
