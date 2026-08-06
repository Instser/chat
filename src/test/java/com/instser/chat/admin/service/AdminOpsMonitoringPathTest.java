package com.instser.chat.admin.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminOpsMonitoringPathTest {
    @Test
    void filtersMonitoringPaths() {
        assertTrue(AdminOpsService.isMonitoringSelfPath("/admin/ops/monitoring"));
        assertTrue(AdminOpsService.isMonitoringSelfPath("/api/admin/ops/monitoring"));
        assertTrue(AdminOpsService.isMonitoringSelfPath("/admin/ops/monitoring/models-probe"));
        assertFalse(AdminOpsService.isMonitoringSelfPath("/admin/ops/health"));
        assertFalse(AdminOpsService.isMonitoringSelfPath("/api/chat"));
    }
}
