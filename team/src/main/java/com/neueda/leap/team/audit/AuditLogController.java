package com.neueda.leap.team.audit;

import com.neueda.leap.team.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit logs")
public class AuditLogController {
    private final AuditLogQueryService service;

    public AuditLogController(AuditLogQueryService service) { this.service = service; }

    @GetMapping("/audit-logs")
    @Operation(summary = "List audit logs (ADMIN)",
            description = "Newest first. from is inclusive; to is exclusive. Dates use ISO-8601 UTC instants.")
    public PageResponse<AuditLogDto> list(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long subjectUserId,
            @RequestParam(required = false) String affectedTable,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(actorUserId, subjectUserId, affectedTable, actionType, from, to, page, size);
    }

    @GetMapping("/audit-logs/{auditId}")
    @Operation(summary = "Get an audit log (ADMIN)")
    public AuditLogDto get(@PathVariable Long auditId) { return service.get(auditId); }

    @GetMapping("/users/{userId}/audit-logs")
    @Operation(summary = "List audit logs affecting a user (ADMIN)")
    public PageResponse<AuditLogDto> forUser(
            @PathVariable Long userId,
            @RequestParam(required = false) String affectedTable,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.forUser(userId, affectedTable, actionType, from, to, page, size);
    }
}
