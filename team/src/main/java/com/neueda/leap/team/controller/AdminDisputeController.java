package com.neueda.leap.team.controller;

import com.neueda.leap.team.dto.AdminDisputeDto;
import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/disputes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Disputes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeController {
    private final DisputeService disputeService;

    public AdminDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    @Operation(summary = "List disputes across all accounts",
               description = "ADMIN only. Newest first. status filters by dispute status; q matches client name, email, dispute type or description (contains), or a client, account or dispute ID (exact).")
    public PageResponse<AdminDisputeDto> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return disputeService.searchAllDisputes(status, q, page, size);
    }
}
