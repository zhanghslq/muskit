package io.github.zhanghslq.muskit.idempotency.spi;

import io.github.zhanghslq.muskit.idempotency.model.IdempotencyResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 使用稳定二进制格式编码和解码可重放幂等结果。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyResultCodec {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_HEADER_COUNT = 64;
    private static final int MAX_TEXT_BYTES = 1_048_576;
    private static final int MAX_BODY_BYTES = 16_777_216;

    /**
     * 禁止实例化结果编解码工具。
     */
    private IdempotencyResultCodec() {
    }

    /**
     * 将可重放结果编码为稳定二进制格式。
     *
     * @param result 可重放结果
     * @return 编码后的字节
     */
    public static byte[] encode(IdempotencyResult result) {
        Objects.requireNonNull(result, "可重放结果不能为空");
        if (result.headers().size() > MAX_HEADER_COUNT) {
            throw new IllegalArgumentException("可重放响应头数量不能超过 " + MAX_HEADER_COUNT);
        }
        byte[] body = result.body();
        if (body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("可重放响应体不能超过 " + MAX_BODY_BYTES + " 字节");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(FORMAT_VERSION);
                output.writeInt(result.statusCode());
                writeString(output, result.contentType());
                output.writeInt(result.headers().size());
                for (Map.Entry<String, String> header : result.headers().entrySet()) {
                    writeString(output, header.getKey());
                    writeString(output, header.getValue());
                }
                output.writeInt(body.length);
                output.write(body);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码幂等响应结果", exception);
        }
    }

    /**
     * 从稳定二进制格式解码可重放结果。
     *
     * @param encoded 编码后的字节
     * @return 可重放结果
     */
    public static IdempotencyResult decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "幂等响应编码不能为空");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("不支持的幂等响应格式版本: " + version);
            }
            int statusCode = input.readInt();
            String contentType = readString(input);
            int headerCount = input.readInt();
            if (headerCount < 0 || headerCount > MAX_HEADER_COUNT) {
                throw new IllegalArgumentException("幂等响应头数量无效");
            }
            Map<String, String> headers = new LinkedHashMap<>();
            for (int index = 0; index < headerCount; index++) {
                headers.put(readString(input), readString(input));
            }
            int bodyLength = input.readInt();
            if (bodyLength < 0 || bodyLength > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("幂等响应体长度无效");
            }
            byte[] body = input.readNBytes(bodyLength);
            if (body.length != bodyLength || input.read() != -1) {
                throw new IllegalArgumentException("幂等响应编码长度无效");
            }
            return new IdempotencyResult(statusCode, contentType, headers, body);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("幂等响应编码不完整", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法解码幂等响应结果", exception);
        }
    }

    /**
     * 写入带长度的 UTF-8 文本。
     *
     * @param output 二进制输出
     * @param value 文本值
     * @throws IOException 写入失败
     */
    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("幂等响应文本字段过长");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    /**
     * 读取带长度的 UTF-8 文本。
     *
     * @param input 二进制输入
     * @return 文本值
     * @throws IOException 读取失败
     */
    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("幂等响应文本字段长度无效");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("幂等响应文本字段不完整");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
