package io.github.zhanghslq.muskit.audit.spi;

import java.util.Optional;

/**
 * 获取当前审计操作者的可替换 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface AuditPrincipalProvider {

    /**
     * 返回当前操作者标识。
     *
     * @return 当前操作者，不存在时为空
     */
    Optional<String> currentPrincipal();
}
