package com.neueda.leap.team.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Ownership comes from the authenticated CLIENT, never from the request body.
public record CreateAccountRequest(
    @Pattern(regexp = "USD", message = "must be USD") String currency,
    @Size(max = 20, message = "must be 20 characters or less") String accountName) {
    public CreateAccountRequest { currency = currency == null ? "USD" : currency; }
}
