package io.github.zhanghslq.muskit.idempotency.http;

import io.github.zhanghslq.muskit.idempotency.model.IdempotencyResult;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 将响应继续写给客户端的同时，有界捕获可安全重放的响应内容。
 *
 * @author zhs
 * @since 2026-08-20
 */
final class ReplayCaptureResponseWrapper extends HttpServletResponseWrapper {

    private final int maxBodyBytes;
    private final Set<String> replayHeaderNames;
    private final Predicate<String> replayableContentType;
    private final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean overflow;

    /**
     * 创建有界响应捕获包装器。
     *
     * @param response 原始响应
     * @param maxBodyBytes 最大捕获响应体字节数
     * @param replayHeaderNames 允许重放的响应头名称
     * @param replayableContentType 可重放内容类型判定器
     */
    ReplayCaptureResponseWrapper(
            HttpServletResponse response,
            int maxBodyBytes,
            Set<String> replayHeaderNames,
            Predicate<String> replayableContentType) {
        super(response);
        this.maxBodyBytes = maxBodyBytes;
        this.replayHeaderNames = replayHeaderNames;
        this.replayableContentType = replayableContentType;
    }

    /**
     * 返回同时写入客户端和捕获缓冲区的输出流。
     *
     * @return Servlet 输出流
     * @throws IOException 无法创建输出流
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("已经通过 Writer 写入 HTTP 响应");
        }
        if (outputStream == null) {
            outputStream = new CapturingServletOutputStream(super.getOutputStream());
        }
        return outputStream;
    }

    /**
     * 返回同时写入客户端和捕获缓冲区的字符 Writer。
     *
     * @return 响应 Writer
     * @throws IOException 无法创建 Writer
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null && writer == null) {
            throw new IllegalStateException("已经通过 OutputStream 写入 HTTP 响应");
        }
        if (writer == null) {
            Charset charset = Charset.forName(getCharacterEncoding());
            outputStream = new CapturingServletOutputStream(super.getOutputStream());
            writer = new PrintWriter(new OutputStreamWriter(outputStream, charset));
        }
        return writer;
    }

    /**
     * 重置响应体时同步清空捕获缓冲区。
     */
    @Override
    public void resetBuffer() {
        super.resetBuffer();
        capturedBody.reset();
        overflow = false;
    }

    /**
     * 重置完整响应时同步清空捕获状态。
     */
    @Override
    public void reset() {
        super.reset();
        capturedBody.reset();
        overflow = false;
    }

    /**
     * 在响应满足大小和内容类型限制时生成可重放快照。
     *
     * @return 可重放快照，不满足缓存策略时返回空
     */
    Optional<IdempotencyResult> snapshot() {
        if (writer != null) {
            writer.flush();
        }
        String contentType = getContentType();
        if (overflow
                || (contentType == null && capturedBody.size() > 0)
                || !replayableContentType.test(contentType == null ? "" : contentType)) {
            return Optional.empty();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : replayHeaderNames) {
            String headerValue = getHeader(headerName);
            if (headerValue != null) {
                headers.put(headerName, headerValue);
            }
        }
        return Optional.of(new IdempotencyResult(
                getStatus(),
                contentType == null ? "" : contentType,
                headers,
                capturedBody.toByteArray()));
    }

    /**
     * 在不超过上限时捕获写出的字节，超过上限后停止缓存但继续返回客户端。
     *
     * @param bytes 待捕获字节
     * @param offset 起始位置
     * @param length 字节数量
     */
    private void capture(byte[] bytes, int offset, int length) {
        if (overflow) {
            return;
        }
        if (capturedBody.size() + length > maxBodyBytes) {
            overflow = true;
            capturedBody.reset();
            return;
        }
        capturedBody.write(bytes, offset, length);
    }

    /**
     * 将响应写入真实客户端输出流并同步复制到有界捕获缓冲区。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private final class CapturingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        /**
         * 创建捕获输出流。
         *
         * @param delegate 客户端输出流
         */
        private CapturingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        /**
         * 返回底层输出流当前是否可写。
         *
         * @return 是否可写
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 将非阻塞写监听器注册到底层输出流。
         *
         * @param listener 写监听器
         */
        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }

        /**
         * 写出并捕获单个字节。
         *
         * @param value 字节值
         * @throws IOException 客户端写入失败
         */
        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            byte[] single = {(byte) value};
            capture(single, 0, 1);
        }

        /**
         * 写出并捕获字节片段。
         *
         * @param bytes 字节数组
         * @param offset 起始位置
         * @param length 字节数量
         * @throws IOException 客户端写入失败
         */
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length);
            capture(bytes, offset, length);
        }

        /**
         * 刷新底层客户端输出流。
         *
         * @throws IOException 刷新失败
         */
        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        /**
         * 关闭底层客户端输出流。
         *
         * @throws IOException 关闭失败
         */
        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
