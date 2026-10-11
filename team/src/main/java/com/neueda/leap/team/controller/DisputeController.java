package com.neueda.leap.team.controller;
import com.neueda.leap.team.dto.DisputeDto;
import com.neueda.leap.team.dto.CreateDisputeRequest;
import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.dto.UpdateDisputeStatusRequest;
import com.neueda.leap.team.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/disputes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Disputes")
@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
public class DisputeController {
    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping("/{accountId}/transaction/{transactionId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "File a dispute on a transaction", description = "CLIENT only. Dispute a specific transaction in their account.")
    public ResponseEntity<DisputeDto> fileDisputeOnTransaction(@PathVariable Long accountId, @PathVariable Long transactionId, @Valid @RequestBody CreateDisputeRequest request)
    {
        DisputeDto dispute = disputeService.fileDisputeOnTransaction(accountId, transactionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }

    @PostMapping("/{accountId}/trade/{tradeId}")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "File a dispute on a trade", 
               description = "CLIENT only. Dispute a specific trade in their account.")
    public ResponseEntity<DisputeDto> fileDisputeOnTrade(
            @PathVariable Long accountId,
            @PathVariable Long tradeId,
            @Valid @RequestBody CreateDisputeRequest request) {
        DisputeDto dispute = disputeService.fileDisputeOnTrade(accountId, tradeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "List disputes for an account", 
               description = "CLIENT sees only their own. ADMIN sees all accounts.")
    public ResponseEntity<PageResponse<DisputeDto>> listDisputes(
            @PathVariable Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<DisputeDto> response = disputeService.listDisputesByAccount(accountId, status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/{disputeId}")
    @Operation(summary = "Get a specific dispute")
    public ResponseEntity<DisputeDto> getDispute(
            @PathVariable Long accountId,
            @PathVariable Long disputeId) {
        DisputeDto dispute = disputeService.getDispute(accountId, disputeId);
        return ResponseEntity.ok(dispute);
    }

    @PostMapping("/{accountId}/{disputeId}/assign-to-me")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign dispute to current ADMIN", 
               description = "ADMIN only. Assign a dispute to review.")
    public ResponseEntity<DisputeDto> assignToMe(
            @PathVariable Long accountId,
            @PathVariable Long disputeId) {
        DisputeDto dispute = disputeService.assignToMe(disputeId);
        return ResponseEntity.ok(dispute);
    }

    @PatchMapping("/{accountId}/{disputeId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update dispute status", 
               description = "ADMIN only. Update status to UNDER_REVIEW, ESCALATED, RESOLVED, or REJECTED.")
    public ResponseEntity<DisputeDto> updateStatus(
            @PathVariable Long accountId,
            @PathVariable Long disputeId,
            @Valid @RequestBody UpdateDisputeStatusRequest request) {
        DisputeDto dispute = disputeService.updateStatus(disputeId, request);
        return ResponseEntity.ok(dispute);
    }
}
