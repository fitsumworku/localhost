package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.CreateDisputeRequest;
import com.neueda.leap.team.dto.DisputeDto;
import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.dto.UpdateDisputeStatusRequest;
import com.neueda.leap.team.entity.Dispute;
import com.neueda.leap.team.entity.enums.DisputeStatus;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.repository.DisputeRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final AccountAccessService access;
    private final Clock clock;

    public DisputeService(DisputeRepository disputeRepository, AccountAccessService access, Clock clock) {
        this.disputeRepository = disputeRepository;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public DisputeDto fileDisputeOnTransaction(long accountId, long transactionId, CreateDisputeRequest request) {
        var user = access.lockActiveClient();
        access.owned(accountId, user.getId());

        if (!disputeRepository.existsTransactionForAccount(accountId, transactionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found for this account.");
        }

        Dispute dispute = disputeRepository.saveAndFlush(Dispute.forTransaction(
            accountId,
            transactionId,
            request.disputeType().strip(),
            normalizeDescription(request.description()),
            clock.instant()));

        return toDto(dispute);
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public DisputeDto fileDisputeOnTrade(long accountId, long tradeId, CreateDisputeRequest request) {
        var user = access.lockActiveClient();
        access.owned(accountId, user.getId());

        if (!disputeRepository.existsTradeForAccount(accountId, tradeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRADE_NOT_FOUND", "Trade not found for this account.");
        }

        Dispute dispute = disputeRepository.saveAndFlush(Dispute.forTrade(
            accountId,
            tradeId,
            request.disputeType().strip(),
            normalizeDescription(request.description()),
            clock.instant()));

        return toDto(dispute);
    }

    public PageResponse<DisputeDto> listDisputesByAccount(long accountId, String status, int page, int size) {
        access.readable(accountId);
        validatePage(page, size);
        DisputeStatus filter = parseStatus(status);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("dateCreated"), Sort.Order.desc("id")));
        var result = filter == null
            ? disputeRepository.findByAccountId(accountId, pageable)
            : disputeRepository.findByAccountIdAndStatus(accountId, filter, pageable);
        return PageResponse.of(result.getContent().stream().map(DisputeService::toDto).toList(), page, size, result.getTotalElements());
    }

    public DisputeDto getDispute(long accountId, long disputeId) {
        access.readable(accountId);
        return disputeRepository.findByAccountIdAndId(accountId, disputeId)
            .map(DisputeService::toDto)
            .orElseThrow(this::notFound);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DisputeDto assignToMe(long disputeId) {
        if (!access.isAdmin()) {
            throw new AccessDeniedException("Admin role required");
        }
        long adminId = access.principal().getUserId();
        Dispute dispute = disputeRepository.findById(disputeId).orElseThrow(this::notFound);
        dispute.assignToAdmin(adminId);
        return toDto(dispute);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DisputeDto updateStatus(long disputeId, UpdateDisputeStatusRequest request) {
        if (!access.isAdmin()) {
            throw new AccessDeniedException("Admin role required");
        }
        Dispute dispute = disputeRepository.findById(disputeId).orElseThrow(this::notFound);
        dispute.changeStatus(request.disputeStatus(), clock.instant());
        return toDto(dispute);
    }

    private DisputeStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return DisputeStatus.valueOf(rawStatus.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISPUTE_STATUS", "Invalid dispute status filter.");
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found.");
    }

    private static DisputeDto toDto(Dispute dispute) {
        return new DisputeDto(
                dispute.getId(),
                dispute.getAccountId(),
                dispute.getAdminId(),
                dispute.getTransactionId(),
                dispute.getTradeId(),
                dispute.getDisputeType(),
                dispute.getDescription(),
                dispute.getStatus(),
                dispute.getDateCreated(),
                dispute.getDateResolved()
        );
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be nonnegative; size must be between 1 and 100.");
        }
    }

}
