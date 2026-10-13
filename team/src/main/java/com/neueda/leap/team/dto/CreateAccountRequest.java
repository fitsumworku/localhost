package com.neueda.leap.team.dto;

import jakarta.validation.constraints.Pattern;

// Ownership comes from the authenticated CLIENT, never from the request body.
public record CreateAccountRequest(@Pattern(regexp = "USD", message = "must be USD") String currency) {
    public CreateAccountRequest { currency = currency == null ? "USD" : currency; }
}
