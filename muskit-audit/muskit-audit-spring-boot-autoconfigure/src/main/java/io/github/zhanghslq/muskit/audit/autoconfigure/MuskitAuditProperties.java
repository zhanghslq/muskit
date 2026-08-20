package io.github.zhanghslq.muskit.audit.autoconfigure;

import io.github.zhanghslq.muskit.audit.AuditFailureMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit 审计配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.audit")
public class MuskitAuditProperties {

    private boolean enabled = true;
    private AuditFailureMode failureMode = AuditFailureMode.FAIL_FAST;
    private String provider = "jdbc";
    private String tableName = "muskit_audit";

    /**
     * 返回是否启用审计。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用审计。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回审计失败模式。
     *
     * @return 失败模式
     */
    public AuditFailureMode getFailureMode() {
        return failureMode;
    }

    /**
     * 设置审计失败模式。
     *
     * @param failureMode 失败模式
     */
    public void setFailureMode(AuditFailureMode failureMode) {
        this.failureMode = failureMode;
    }

    /**
     * 返回审计 Provider 名称。
     *
     * @return Provider 名称
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置审计 Provider 名称。
     *
     * @param provider Provider 名称
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 返回 JDBC 审计表名。
     *
     * @return 审计表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 设置 JDBC 审计表名。
     *
     * @param tableName 审计表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
