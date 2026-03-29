package com.ai.trainer.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.Buffer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * OkHttp 日志拦截器
 * 统一打印 API 调用的入参和出参
 */
@Slf4j
public class LoggingInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // 打印请求信息
        logRequest(request);

        // 执行请求
        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            log.error("API请求异常: {} - {}", request.url(), e.getMessage());
            throw e;
        }

        // 打印响应信息并返回新的response（因为body被读取了）
        return logResponse(response);
    }

    private void logRequest(Request request) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== API请求 ==========\n");
        sb.append("URL: ").append(request.url()).append("\n");
        sb.append("Method: ").append(request.method()).append("\n");

        // 打印请求头
        sb.append("Headers:\n");
        request.headers().names().forEach(name -> {
            String value = request.header(name);
            // 隐藏敏感信息
            if ("Authorization".equalsIgnoreCase(name)) {
                value = maskApiKey(value);
            }
            sb.append("  ").append(name).append(": ").append(value).append("\n");
        });

        // 打印请求体
        RequestBody body = request.body();
        if (body != null) {
            try {
                Buffer buffer = new Buffer();
                body.writeTo(buffer);
                Charset charset = StandardCharsets.UTF_8;
                String bodyString = buffer.readString(charset);
                sb.append("Body: ").append(bodyString).append("\n");
            } catch (Exception e) {
                sb.append("Body: [无法读取]\n");
            }
        }

        sb.append("============================");
        log.info(sb.toString());
    }

    private Response logResponse(Response response) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== API响应 ==========\n");
        sb.append("URL: ").append(response.request().url()).append("\n");
        sb.append("Status: ").append(response.code()).append("\n");

        // 打印响应头
        sb.append("Headers:\n");
        response.headers().forEach(header ->
            sb.append("  ").append(header.getFirst()).append(": ").append(header.getSecond()).append("\n")
        );

        // 打印响应体
        ResponseBody responseBody = response.body();
        byte[] bodyBytes = null;
        if (responseBody != null) {
            bodyBytes = responseBody.bytes();

            // 判断是否为二进制内容（图片、视频等）
            String contentType = responseBody.contentType() != null ? responseBody.contentType().toString() : "";
            boolean isBinary = contentType.contains("image") || contentType.contains("video") ||
                               contentType.contains("audio") || contentType.contains("octet-stream");

            if (isBinary) {
                sb.append("Body: [二进制数据, 大小: ").append(bodyBytes.length).append(" bytes]\n");
            } else if (bodyBytes.length > 10000) {
                // 大文本只显示前1000字符
                String bodyString = new String(bodyBytes, StandardCharsets.UTF_8);
                sb.append("Body: ").append(bodyString.substring(0, Math.min(1000, bodyString.length())));
                if (bodyString.length() > 1000) {
                    sb.append("... (共").append(bodyString.length()).append("字符)");
                }
                sb.append("\n");
            } else {
                String bodyString = new String(bodyBytes, StandardCharsets.UTF_8);
                sb.append("Body: ").append(bodyString).append("\n");
            }
        }

        sb.append("============================");
        if (response.isSuccessful()) {
            log.info(sb.toString());
        } else {
            log.error(sb.toString());
        }

        // 使用原始字节数组重新创建ResponseBody，保证二进制数据不损坏
        ResponseBody newBody = bodyBytes != null
            ? ResponseBody.create(bodyBytes, responseBody.contentType())
            : responseBody;
        return response.newBuilder().body(newBody).build();
    }

    private String maskApiKey(String value) {
        if (value == null) return null;
        if (value.startsWith("Bearer ")) {
            String key = value.substring(7);
            if (key.length() > 8) {
                return "Bearer " + key.substring(0, 4) + "..." + key.substring(key.length() - 4);
            }
        }
        return "***";
    }
}