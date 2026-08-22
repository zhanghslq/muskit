package io.github.zhanghslq.muskit.lifecycle.service;

import io.github.zhanghslq.muskit.lifecycle.model.DrainReport;
import io.github.zhanghslq.muskit.lifecycle.model.DrainSnapshot;
import io.github.zhanghslq.muskit.lifecycle.model.DrainState;
import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import io.github.zhanghslq.muskit.lifecycle.service.DrainCoordinator;
import io.github.zhanghslq.muskit.lifecycle.service.DrainPermit;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 排空控制器与协调器测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class DrainControllerTest {

    /**
     * 验证开始排空后拒绝新工作，并在许可释放后完成排空。
     */
    @Test
    void shouldRejectNewWorkAndAwaitExistingWork() {
        DrainController controller = new DrainController("http");
        DrainPermit permit = controller.tryEnter().orElseThrow();

        controller.beginDrain();

        assertThat(controller.tryEnter()).isEmpty();
        assertThat(controller.awaitDrained(Duration.ZERO)).isFalse();
        permit.close();
        permit.close();
        assertThat(controller.awaitDrained(Duration.ofMillis(10))).isTrue();
        assertThat(controller.snapshot()).isEqualTo(new DrainSnapshot("http", DrainState.DRAINED, 0L));
    }

    /**
     * 验证排空结束后可以显式恢复接流。
     */
    @Test
    void shouldResumeAfterFullyDrained() {
        DrainController controller = new DrainController("requests");
        controller.beginDrain();
        controller.startAccepting();

        assertThat(controller.tryEnter()).isPresent().get().satisfies(DrainPermit::close);
        assertThat(controller.snapshot().state()).isEqualTo(DrainState.RUNNING);
    }

    /**
     * 验证协调器使用共享超时并报告未排空组件。
     */
    @Test
    void shouldReportIncompleteComponents() {
        DrainController first = new DrainController("first");
        DrainController second = new DrainController("second");
        DrainPermit firstPermit = first.tryEnter().orElseThrow();
        DrainPermit secondPermit = second.tryEnter().orElseThrow();
        DrainCoordinator coordinator = new DrainCoordinator(List.of(first, second));

        DrainReport report = coordinator.drain(Duration.ZERO);

        assertThat(report.completed()).isFalse();
        assertThat(report.incompleteComponents()).containsExactly("first", "second");
        firstPermit.close();
        secondPermit.close();
    }

    /**
     * 验证重复组件名称会被拒绝，避免状态汇总歧义。
     */
    @Test
    void shouldRejectDuplicateComponentNames() {
        assertThatThrownBy(() -> new DrainCoordinator(List.of(
                new DrainController("duplicate"),
                new DrainController("duplicate"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
