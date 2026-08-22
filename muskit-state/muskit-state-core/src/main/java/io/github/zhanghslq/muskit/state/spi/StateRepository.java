package io.github.zhanghslq.muskit.state.spi;

import io.github.zhanghslq.muskit.state.model.VersionedState;
import java.util.Optional;

/**
 * 状态实体读取和乐观锁更新 SPI。
 *
 * @param <I> 实体标识类型
 * @param <S> 状态类型
 * @author zhs
 * @since 2026-08-20
 */
public interface StateRepository<I, S> {

    /**
     * 读取当前版本化状态。
     *
     * @param id 实体标识
     * @return 状态快照，不存在时为空
     */
    Optional<VersionedState<I, S>> find(I id);

    /**
     * 仅当版本匹配时更新状态并将版本递增一。
     *
     * @param id 实体标识
     * @param expectedVersion 期望版本
     * @param newState 新状态
     * @return 是否更新成功
     */
    boolean compareAndSet(I id, long expectedVersion, S newState);
}
