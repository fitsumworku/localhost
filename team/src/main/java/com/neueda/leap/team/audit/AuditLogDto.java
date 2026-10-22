package com.neueda.leap.team.audit;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record AuditLogDto(Long auditId, Long actorUserId, String actorType,
                          Long subjectUserId, String affectedTable, JsonNode recordKey,
                          String actionType, JsonNode oldValue, JsonNode newValue,
                          Instant timestamp) {}
