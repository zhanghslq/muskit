package io.github.zhanghslq.muskit.state;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态迁移和乐观锁执行测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class StateMachineTest {

    /**
     * 验证守卫允许和拒绝的明确结果。
     */
    @Test
    void shouldApplyOrRejectGuardedTransition() {
        StateMachineDefinition<String, String, Integer> definition = StateMachineDefinition
                .<String, String, Integer>builder()
                .transition("CREATED", "PAY", "PAID", (state, event, amount) -> amount > 0)
                .build();
        StateMachine<String, String, Integer> machine = new StateMachine<>(definition);

        assertThat(machine.transition("CREATED", "PAY", 1).status()).isEqualTo(StateTransitionStatus.APPLIED);
        assertThat(machine.transition("CREATED", "PAY", 0).status()).isEqualTo(StateTransitionStatus.GUARD_REJECTED);
        assertThat(machine.transition("PAID", "PAY", 1).status()).isEqualTo(StateTransitionStatus.NO_TRANSITION);
    }

    /**
     * 验证重复状态事件规则在构建时被拒绝。
     */
    @Test
    void shouldRejectDuplicateTransition() {
        StateMachineDefinition.Builder<String, String, Void> builder = StateMachineDefinition.builder();
        builder.transition("A", "GO", "B");

        assertThatThrownBy(() -> builder.transition("A", "GO", "C"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证乐观锁冲突后会读取最新状态并重试。
     */
    @Test
    void shouldRetryOptimisticConflict() {
        AtomicReference<VersionedState<String, String>> state =
                new AtomicReference<>(new VersionedState<>("id-1", "CREATED", 1));
        AtomicInteger updates = new AtomicInteger();
        StateRepository<String, String> repository = new StateRepository<>() {
            /**
             * 返回当前测试状态。
             *
             * @param id 实体标识
             * @return 当前状态
             */
            @Override
            public Optional<VersionedState<String, String>> find(String id) {
                return Optional.of(state.get());
            }

            /**
             * 第一次模拟冲突，第二次提交成功。
             *
             * @param id 实体标识
             * @param expectedVersion 期望版本
             * @param newState 新状态
             * @return 是否成功
             */
            @Override
            public boolean compareAndSet(String id, long expectedVersion, String newState) {
                if (updates.getAndIncrement() == 0) {
                    state.set(new VersionedState<>(id, "CREATED", 2));
                    return false;
                }
                state.set(new VersionedState<>(id, newState, expectedVersion + 1));
                return true;
            }
        };
        StateMachineDefinition<String, String, Void> definition = StateMachineDefinition
                .<String, String, Void>builder().transition("CREATED", "PAY", "PAID").build();

        PersistentStateResult<String> result = new StateMachineFactory(2)
                .createPersistent(definition, repository)
                .fire("id-1", "PAY", null);

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.version()).isEqualTo(3);
        assertThat(state.get().state()).isEqualTo("PAID");
    }
}
