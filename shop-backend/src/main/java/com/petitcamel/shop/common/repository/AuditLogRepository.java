package com.petitcamel.shop.common.repository;

import com.petitcamel.shop.common.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
