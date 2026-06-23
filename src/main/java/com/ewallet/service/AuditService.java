package com.ewallet.service;

import com.ewallet.entity.AuditLog;
import com.ewallet.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Records audit events asynchronously in a <em>new, independent transaction</em>.
 *
 * <p>Using {@code Propagation.REQUIRES_NEW} means:
 * <ol>
 *   <li>Audit writes commit even if the calling transaction rolls back.</li>
 *   <li>A failure to write an audit entry does <b>not</b> roll back the business transaction.</li>
 * </ol>
 *
 * <p>The {@code @Async} annotation offloads the write to a separate thread pool
 * so the hot transaction path is not blocked by audit I/O.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, UUID userId, String entityType,
                    String entityId, String ipAddress, Map<String, Object> details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .userId(userId)
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ipAddress)
                    .details(details)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit write failures must NOT propagate to the caller.
            // Log to the application log and optionally push to a dead-letter queue.
            log.error("Failed to write audit log [action={}, userId={}]: {}", action, userId, e.getMessage());
        }
    }
}
