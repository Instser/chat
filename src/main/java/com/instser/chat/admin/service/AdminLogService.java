package com.instser.chat.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员日志查看服务。
 * <p>读取 logback 配置输出的日志文件尾部内容，支持按 traceId / 关键词 / 级别筛选。
 * 日志文件位于 {@code logs/} 相对目录（logback LOG_HOME=logs，supervisor directory=/opt/chat/）。
 *
 * @author instser
 * @since 2026-08-26
 */
@Slf4j
@Service
public class AdminLogService {

    private static final String LOG_DIR = "logs";
    /** 支持查看的日志文件（白名单，避免路径穿越） */
    private static final List<String> ALLOWED_FILES = Arrays.asList(
            "jinyu-chat-all.log",
            "jinyu-chat-error.log",
            "jinyu-chat-monitor.log"
    );
    private static final int DEFAULT_LINES = 500;
    private static final int MAX_LINES = 5000;
    private static final int MAX_LINE_BYTES = 20000;

    /**
     * 列出可用日志文件及状态。
     */
    public List<Map<String, Object>> listFiles() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String fileName : ALLOWED_FILES) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("fileName", fileName);
            java.io.File f = new java.io.File(LOG_DIR, fileName);
            info.put("exists", f.exists());
            if (f.exists()) {
                info.put("size", f.length());
                info.put("lastModified", f.lastModified());
            }
            result.add(info);
        }
        return result;
    }

    /**
     * 读取日志文件尾部并筛选。
     *
     * @param fileName 日志文件名（白名单校验）
     * @param maxLines 读取的最大行数（尾部，默认 500，上限 5000）
     * @param traceId  按 traceId 筛选（包含匹配）
     * @param keyword   关键词筛选（包含匹配，忽略大小写）
     * @param level     日志级别筛选（ERROR/WARN/INFO/DEBUG，匹配日志行中的级别标记）
     * @return 筛选后的日志行列表
     */
    public Map<String, Object> tail(String fileName, Integer maxLines,
                                    String traceId, String keyword, String level) {
        if (!ALLOWED_FILES.contains(fileName)) {
            throw new IllegalArgumentException("不支持的日志文件: " + fileName);
        }
        int limit = maxLines == null || maxLines <= 0 ? DEFAULT_LINES : Math.min(maxLines, MAX_LINES);

        List<String> lines = readTailLines(new java.io.File(LOG_DIR, fileName), limit);

        // 筛选
        List<String> filtered = lines.stream()
                .filter(line -> matchTraceId(line, traceId))
                .filter(line -> matchKeyword(line, keyword))
                .filter(line -> matchLevel(line, level))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("totalRead", lines.size());
        result.put("filtered", filtered.size());
        result.put("lines", filtered);
        return result;
    }

    /**
     * 高效读取文件尾部指定行数（从后向前读）。
     */
    private List<String> readTailLines(java.io.File file, int maxLines) {
        List<String> result = new ArrayList<>();
        if (!file.exists() || !file.isFile()) {
            return result;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return result;
            }
            // 从文件末尾向前读取，按块读入，直到收集够 maxLines 行
            long pointer = fileLength;
            StringBuilder chunk = new StringBuilder();
            boolean needMore = true;
            int lineCount = 0;

            while (pointer > 0 && needMore) {
                long move = Math.min(pointer, 4096L);
                pointer -= move;
                raf.seek(pointer);
                byte[] buf = new byte[(int) move];
                raf.readFully(buf);
                chunk.insert(0, new String(buf, StandardCharsets.UTF_8));

                // 按换行拆分，从尾部收集行
                String content = chunk.toString();
                int idx = content.length();
                while (true) {
                    int nl = content.lastIndexOf('\n', idx - 1);
                    if (nl < 0) {
                        break;
                    }
                    String line = content.substring(nl + 1, idx);
                    idx = nl;
                    if (line.isEmpty() && nl == 0) {
                        continue;
                    }
                    if (line.length() <= MAX_LINE_BYTES) {
                        result.add(0, line);
                    }
                    lineCount++;
                    if (lineCount >= maxLines) {
                        needMore = false;
                        break;
                    }
                }
                // 保留未处理完的头部片段
                chunk = new StringBuilder(content.substring(0, idx));
            }
            // 处理文件开头的剩余片段
            if (needMore && chunk.length() > 0) {
                String line = chunk.toString();
                if (!line.isEmpty() && line.length() <= MAX_LINE_BYTES) {
                    result.add(0, line);
                }
            }
        } catch (IOException e) {
            log.warn("读取日志文件失败 file={} err={}", file.getName(), e.getMessage());
            result.add(0, "[读取失败: " + e.getMessage() + "]");
        }
        return result;
    }

    /**
     * traceId 匹配：日志行中包含 [traceId]。
     */
    private boolean matchTraceId(String line, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return true;
        }
        return line.contains("[" + traceId.trim() + "]");
    }

    /**
     * 关键词匹配：忽略大小写的包含匹配。
     */
    private boolean matchKeyword(String line, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return line.toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * 级别匹配：日志行的级别标记形如 " ERROR " / " WARN " / " INFO " / " DEBUG "。
     */
    private boolean matchLevel(String line, String level) {
        if (level == null || level.isBlank() || "ALL".equalsIgnoreCase(level)) {
            return true;
        }
        return line.contains(" " + level.trim().toUpperCase() + " ");
    }
}
