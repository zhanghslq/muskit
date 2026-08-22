package io.github.zhanghslq.muskit.audit.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 不可变审计事件，字符串描述不会输出主体、操作者或扩展属性值。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class AuditEvent {

    private final String eventId;
    private final Instant occurredAt;
    private final String action;
    private final AuditOutcome outcome;
    private final String actor;
    private final String subjectType;
    private final String subjectId;
    private final String errorCode;
    private final Map<String, String> attributes;

    /**
     * 创建审计事件。
     *
     * @param eventId 审计事件唯一标识
     * @param occurredAt 发生时间
     * @param action 稳定操作名称
     * @param outcome 操作结果
     * @param actor 操作者，可为空
     * @param subjectType 主体类型，可为空
     * @param subjectId 主体标识，可为空
     * @param errorCode 稳定错误码，可为空
     * @param attributes 扩展属性
     */
    public AuditEvent(
            String eventId,
            Instant occurredAt,
            String action,
            AuditOutcome outcome,
            String actor,
            String subjectType,
            String subjectId,
            String errorCode,
            Map<String, String> attributes) {
        this.eventId = requireText(eventId, "审计事件标识不能为空", 128);
        this.occurredAt = Objects.requireNonNull(occurredAt, "审计事件时间不能为空");
        this.action = requireText(action, "审计操作名称不能为空", 128);
        this.outcome = Objects.requireNonNull(outcome, "审计操作结果不能为空");
        this.actor = optionalText(actor, 256);
        this.subjectType = optionalText(subjectType, 128);
        this.subjectId = optionalText(subjectId, 256);
        this.errorCode = optionalText(errorCode, 128);
        Objects.requireNonNull(attributes, "审计扩展属性不能为空");
        if (attributes.size() > 32) {
            throw new IllegalArgumentException("审计扩展属性不能超过 32 项");
        }
        attributes.forEach((key, value) -> {
            requireText(key, "审计扩展属性键不能为空", 128);
            requireText(value, "审计扩展属性值不能为空", 2048);
        });
        this.attributes = Map.copyOf(attributes);
    }

    /**
     * 返回事件标识。
     *
     * @return 事件标识
     */
    public String eventId() {
        return eventId;
    }

    /**
     * 返回发生时间。
     *
     * @return 发生时间
     */
    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * 返回稳定操作名称。
     *
     * @return 操作名称
     */
    public String action() {
        return action;
    }

    /**
     * 返回操作结果。
     *
     * @return 操作结果
     */
    public AuditOutcome outcome() {
        return outcome;
    }

    /**
     * 返回操作者。
     *
     * @return 操作者
     */
    public Optional<String> actor() {
        return Optional.ofNullable(actor);
    }

    /**
     * 返回主体类型。
     *
     * @return 主体类型
     */
    public Optional<String> subjectType() {
        return Optional.ofNullable(subjectType);
    }

    /**
     * 返回主体标识。
     *
     * @return 主体标识
     */
    public Optional<String> subjectId() {
        return Optional.ofNullable(subjectId);
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public Optional<String> errorCode() {
        return Optional.ofNullable(errorCode);
    }

    /**
     * 返回不可变扩展属性。
     *
     * @return 扩展属性
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * 返回隐藏高基数和敏感值的描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "AuditEvent{action='" + action + "', outcome=" + outcome
                + ", attributeKeys=" + attributes.keySet() + '}';
    }

    /**
     * 校验必填文本。
     *
     * @param value 文本值
     * @param message 失败消息
     * @param maxLength 最大长度
     * @return 原文本
     */
    private static String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(message + "且长度不能超过 " + maxLength);
        }
        return value;
    }

    /**
     * 校验可选文本。
     *
     * @param value 文本值
     * @param maxLength 最大长度
     * @return 规范后的文本
     */
    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("审计可选字段长度不能超过 " + maxLength);
        }
        return value;
    }
}
