package io.github.zhanghslq.muskit.example.executor;

import io.github.zhanghslq.muskit.concurrency.annotation.ConcurrencyGuard;
import org.springframework.stereotype.Service;

/**
 * 演示按租户限制并发数的导出服务。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class TenantExportService {

    /**
     * 创建租户导出服务。
     */
    public TenantExportService() {
    }

    /**
     * 执行指定租户的数据导出。
     *
     * @param tenantId 租户标识
     * @return 导出结果描述
     */
    @ConcurrencyGuard(policy = "tenant-export", key = "#tenantId")
    public String export(String tenantId) {
        return "exported:" + tenantId;
    }
}
