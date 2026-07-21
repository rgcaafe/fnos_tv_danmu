package com.fntv.app;

import android.util.Log;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双语 ASS 字幕合并处理器
 * 将时间重叠的日文+中文 Dialogue 合并成一行，用 \N 分隔，使 ExoPlayer 同时显示两条
 */
public class BilingualAssProcessor {

    private static final String TAG = "AssProcessor";

    /** 合并后的 ASS 内容 */
    public static class ProcessedAss {
        public final String content;
        public final int mergedCount;

        public ProcessedAss(String content, int mergedCount) {
            this.content = content;
            this.mergedCount = mergedCount;
        }
    }

    /**
     * 解析 ASS 内容，合并同一时间段的多条 Dialogue
     * @param rawAss 原始 ASS 文本
     * @return 处理后的 ASS
     */
    public static ProcessedAss process(String rawAss) {
        if (rawAss == null || rawAss.isEmpty()) {
            return new ProcessedAss(rawAss, 0);
        }

        List<String> headerLines = new ArrayList<>();
        List<AssDialogue> dialogues = new ArrayList<>();
        List<String> formatLine = new ArrayList<>();

        BufferedReader reader = new BufferedReader(new StringReader(rawAss));
        try {
            String line;
            boolean inEvents = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Dialogue:")) {
                    AssDialogue d = parseDialogue(line);
                    if (d != null) dialogues.add(d);
                } else if (line.startsWith("Format:") && inEvents) {
                    formatLine.add(line);
                } else if (line.startsWith("[Events]")) {
                    inEvents = true;
                    headerLines.add(line);
                } else if (line.startsWith("[Script Info]") || line.startsWith("[V4+ Styles]")
                        || line.startsWith("[V4 Styles]") || line.startsWith("[Aegisub")
                        || line.startsWith("[Fonts]") || line.startsWith("[Graphics]")
                        || line.startsWith("Style:") || line.startsWith("Comment:")
                        || line.startsWith("PlayResX") || line.startsWith("PlayResY")
                        || line.startsWith("ScaledBorderAndShadow") || line.startsWith("Video")
                        || line.startsWith("Audio") || line.startsWith("Timer")) {
                    headerLines.add(line);
                } else if (!line.trim().isEmpty() && !line.startsWith(";") && !line.startsWith("!")) {
                    // 非空非注释行
                    if (line.startsWith("[")) {
                        headerLines.add(line);
                    } else if (!inEvents) {
                        headerLines.add(line);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取 ASS 失败", e);
            return new ProcessedAss(rawAss, 0);
        }

        if (dialogues.isEmpty()) {
            return new ProcessedAss(rawAss, 0);
        }

        // 按开始时间排序
        Collections.sort(dialogues, new Comparator<AssDialogue>() {
            @Override public int compare(AssDialogue a, AssDialogue b) {
                return Long.compare(a.startMs, b.startMs);
            }
        });

        // 严格时间重叠合并：同一时间段有多条 Dialogue 就合并成一行
        List<AssDialogue> merged = new ArrayList<>();
        boolean[] used = new boolean[dialogues.size()];
        int mergeCount = 0;

        for (int i = 0; i < dialogues.size(); i++) {
            if (used[i]) continue;
            AssDialogue current = dialogues.get(i);
            List<AssDialogue> group = new ArrayList<>();
            group.add(current);
            used[i] = true;

            // 往后找所有严格时间重叠的 Dialogue
            for (int j = i + 1; j < dialogues.size(); j++) {
                if (used[j]) continue;
                AssDialogue next = dialogues.get(j);
                // 严格时间重叠：当前条还没结束，且下一条已经开始
                if (current.startMs < next.endMs && next.startMs < current.endMs) {
                    group.add(next);
                    used[j] = true;
                    // 扩展当前时间范围以包含新加入的 Dialogue
                    current = new AssDialogue(
                            Math.min(current.startMs, next.startMs),
                            Math.max(current.endMs, next.endMs),
                            current.style, current.text,
                            current.marginL, current.marginR, current.marginV, current.effect);
                } else if (next.startMs >= current.endMs) {
                    break; // 后面的时间更晚，不用继续
                }
            }

            if (group.size() > 1) {
                // 多条合并：用 \N 分隔
                StringBuilder mergedText = new StringBuilder();
                for (AssDialogue d : group) {
                    if (mergedText.length() > 0) mergedText.append("\\N");
                    mergedText.append(d.text);
                }
                merged.add(new AssDialogue(group.get(0).startMs,
                        group.get(group.size() - 1).endMs,
                        group.get(0).style, mergedText.toString(),
                        group.get(0).marginL, group.get(0).marginR, group.get(0).marginV,
                        group.get(0).effect));
                mergeCount += group.size();
            } else {
                merged.add(current);
            }
        }

        // 重新按时间排序
        Collections.sort(merged, new Comparator<AssDialogue>() {
            @Override public int compare(AssDialogue a, AssDialogue b) {
                return Long.compare(a.startMs, b.startMs);
            }
        });

        // 重新组装 ASS
        StringBuilder sb = new StringBuilder();
        for (String h : headerLines) {
            sb.append(h).append("\n");
        }
        if (!formatLine.isEmpty()) {
            for (String f : formatLine) {
                sb.append(f).append("\n");
            }
        }
        // 没找到 Format 行，加默认
        if (formatLine.isEmpty()) {
            sb.append("Format: Layer, Start, End, Style, Actor, MarginL, MarginR, MarginV, Effect, Text\n");
        }
        for (AssDialogue d : merged) {
            sb.append(d.toDialogueLine()).append("\n");
        }

        return new ProcessedAss(sb.toString(), mergeCount);
    }

    /** 解析 ASS Dialogue 行 */
    private static AssDialogue parseDialogue(String line) {
        // Dialogue: Layer,Start,End,Style,Actor,MarginL,MarginR,MarginV,Effect,Text
        if (!line.startsWith("Dialogue:")) return null;
        String body = line.substring("Dialogue:".length()).trim();
        // ASS 格式严格用逗号分隔前9个字段，剩余是文本
        int commaCount = 0;
        int splitPos = -1;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == ',') {
                commaCount++;
                if (commaCount == 9) {
                    splitPos = i;
                    break;
                }
            }
        }
        if (splitPos < 0) return null;

        String[] parts = new String[9];
        int start = 0;
        int idx = 0;
        for (int i = 0; i < splitPos && idx < 9; i++) {
            if (body.charAt(i) == ',') {
                parts[idx++] = body.substring(start, i);
                start = i + 1;
            }
        }
        // 补全剩余逗号分隔的前9个字段
        while (idx < 9 && start < splitPos) {
            int nextComma = body.indexOf(',', start);
            if (nextComma < 0 || nextComma > splitPos) {
                parts[idx++] = body.substring(start, splitPos);
                break;
            }
            parts[idx++] = body.substring(start, nextComma);
            start = nextComma + 1;
        }

        String text = body.substring(splitPos + 1);
        try {
            long startMs = parseAssTime(parts[1]);
            long endMs = parseAssTime(parts[2]);
            String style = parts.length > 3 ? parts[3] : "";
            int marginL = parts.length > 5 ? parseIntSafe(parts[5]) : 0;
            int marginR = parts.length > 6 ? parseIntSafe(parts[6]) : 0;
            int marginV = parts.length > 7 ? parseIntSafe(parts[7]) : 0;
            String effect = parts.length > 8 ? parts[8] : "";
            return new AssDialogue(startMs, endMs, style, text, marginL, marginR, marginV, effect);
        } catch (Exception e) {
            return null;
        }
    }

    /** ASS 时间格式 0:00:01.23 → 毫秒 */
    private static long parseAssTime(String time) {
        // 格式: H:MM:SS.cc (centiseconds) 或 H:MM:SS.mmm
        try {
            String[] parts = time.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            String[] secParts = parts[2].split("\\.");
            int s = Integer.parseInt(secParts[0]);
            int ms = secParts.length > 1 ? Integer.parseInt(secParts[1]) * (secParts[1].length() == 2 ? 10 : 1) : 0;
            return h * 3600000L + m * 60000L + s * 1000L + ms;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    /** ASS 对话行 */
    private static class AssDialogue {
        long startMs, endMs;
        String style, text, effect;
        int marginL, marginR, marginV;

        AssDialogue(long startMs, long endMs, String style, String text,
                    int marginL, int marginR, int marginV, String effect) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.style = style;
            this.text = text;
            this.marginL = marginL;
            this.marginR = marginR;
            this.marginV = marginV;
            this.effect = effect;
        }

        String toDialogueLine() {
            return String.format("Dialogue: 0,%s,%s,%s,,%d,%d,%d,%s,%s",
                    formatAssTime(startMs), formatAssTime(endMs), style,
                    marginL, marginR, marginV, effect, text);
        }

        private static String formatAssTime(long ms) {
            long totalSec = ms / 1000;
            int h = (int) (totalSec / 3600);
            int m = (int) ((totalSec % 3600) / 60);
            int s = (int) (totalSec % 60);
            int cs = (int) ((ms % 1000) / 10);
            return String.format("%d:%02d:%02d.%02d", h, m, s, cs);
        }
    }
}
