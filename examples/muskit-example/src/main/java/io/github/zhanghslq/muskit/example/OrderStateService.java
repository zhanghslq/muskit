package io.github.zhanghslq.muskit.example;

import io.github.zhanghslq.muskit.audit.Audited;
import io.github.zhanghslq.muskit.state.StateMachine;
import io.github.zhanghslq.muskit.state.StateMachineDefinition;
import io.github.zhanghslq.muskit.state.StateMachineFactory;
import io.github.zhanghslq.muskit.state.StateTransitionResult;
import org.springframework.stereotype.Service;

/**
 * 演示审计注解和无副作用订单状态迁移。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class OrderStateService {

    private final StateMachine<OrderState, OrderEvent, Integer> stateMachine;

    /**
     * 创建订单状态示例服务。
     *
     * @param factory 状态机工厂
     */
    public OrderStateService(StateMachineFactory factory) {
        StateMachineDefinition<OrderState, OrderEvent, Integer> definition = StateMachineDefinition
                .<OrderState, OrderEvent, Integer>builder()
                .transition(OrderState.CREATED, OrderEvent.PAY, OrderState.PAID,
                        (state, event, amount) -> amount != null && amount > 0)
                .build();
        this.stateMachine = factory.create(definition);
    }

    /**
     * 尝试将订单从已创建迁移到已支付，并记录审计结果。
     *
     * @param currentState 当前状态
     * @param amount 支付金额
     * @return 状态迁移结果
     */
    @Audited(action = "order.pay", subjectType = "order")
    public StateTransitionResult<OrderState> pay(OrderState currentState, int amount) {
        return stateMachine.transition(currentState, OrderEvent.PAY, amount);
    }

    /**
     * 示例订单状态。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public enum OrderState {

        /** 已创建。 */
        CREATED,

        /** 已支付。 */
        PAID
    }

    /**
     * 示例订单事件。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public enum OrderEvent {

        /** 支付。 */
        PAY
    }
}
