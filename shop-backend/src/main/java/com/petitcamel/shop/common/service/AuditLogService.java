package com.petitcamel.shop.common.service;

import com.petitcamel.shop.common.domain.AuditLog;
import com.petitcamel.shop.common.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(Long actorMemberId, String action, String targetType, String targetId, String detail) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorMemberId(actorMemberId);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLog.setCreatedAt(clock.instant());
        auditLogRepository.save(auditLog);
    }
}
