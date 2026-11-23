package com.example.documentviewer.resolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure Java OLE2 Compound File Binary Format (CFBF) inspector and best-effort text extractor
 * for legacy Microsoft Office binary formats (.doc and .ppt).
 *
 * Used strictly for PARTIAL offline support: identifies WordDocument / PowerPoint Document
 * streams and extracts readable UTF-16LE / ANSI / Arabic text blocks without claiming full
 * binary layout fidelity.
 */
public final class Ole2LegacyParser {

    private static final long OLE2_SIGNATURE = 0xE11AB1A1E011CFD0L;
    private static final int ENDOFCHAIN = 0xFFFFFFFE;
    private static final int FREESECT = 0xFFFFFFFF;
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    public enum LegacyKind {
        WORD_DOC,
        POWERPOINT_PPT,
        EXCEL_XLS,
        OTHER_OLE2,
        NOT_OLE2
    }

    public static final class InspectionResult {
        public final LegacyKind kind;
        public final List<String> streamNames;
        public final List<String> extractedParagraphs;

        public InspectionResult(
                LegacyKind kind,
                List<String> streamNames,
                List<String> extractedParagraphs
        ) {
            this.kind = kind;
            this.streamNames = streamNames;
            this.extractedParagraphs = extractedParagraphs;
        }
    }

    private Ole2LegacyParser() {}

    public static boolean isOle2Header(byte[] header) {
        if (header == null || header.length < 8) return false;
        ByteBuffer bb = ByteBuffer.wrap(header, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
        return bb.getLong(0) == OLE2_SIGNATURE;
    }

    public static InspectionResult inspect(InputStream inputStream) throws IOException {
        byte[] data = readBounded(inputStream, MAX_BYTES);
        return inspectBytes(data);
    }

    public static InspectionResult inspectBytes(byte[] data) {
        List<String> streams = new ArrayList<>();
        List<String> paragraphs = new ArrayList<>();

        if (data == null || data.length == 0) {
            return new InspectionResult(LegacyKind.NOT_OLE2, streams, paragraphs);
        }

        if (data.length < 512 || !isOle2Header(data)) {
            paragraphs.addAll(extractPlainOrRtfOrXmlParagraphs(data));
            return new InspectionResult(LegacyKind.NOT_OLE2, streams, paragraphs);
        }

        ByteBuffer hdr = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int sectorShift = hdr.getShort(30) & 0xFFFF;
        int sectorSize = 1 << Math.min(16, Math.max(7, sectorShift));
        int numFatSectors = hdr.getInt(44);
        int firstDirSector = hdr.getInt(48);

        int[] fat = readFat(data, hdr, sectorSize, numFatSectors);
        List<DirEntry> entries = readDirectoryEntries(data, fat, sectorSize, firstDirSector);

        boolean hasWordDoc = false;
        boolean hasPowerPointDoc = false;
        boolean hasExcelWorkbook = false;
        DirEntry targetStream = null;

        for (DirEntry entry : entries) {
            if (entry.name != null && !entry.name.isEmpty()) {
                streams.add(entry.name);
                if ("WordDocument".equals(entry.name)) {
                    hasWordDoc = true;
                    targetStream = entry;
                } else if ("PowerPoint Document".equals(entry.name)) {
                    hasPowerPointDoc = true;
                    targetStream = entry;
                } else if ("Workbook".equalsIgnoreCase(entry.name) || "Book".equalsIgnoreCase(entry.name)) {
                    hasExcelWorkbook = true;
                    if (targetStream == null) targetStream = entry;
                }
            }
        }

        if (!hasWordDoc && !hasPowerPointDoc && !hasExcelWorkbook) {
            String rawUtf16 = new String(data, 0, Math.min(data.length, 65536), StandardCharsets.UTF_16LE);
            if (rawUtf16.contains("PowerPoint Document") || rawUtf16.contains("Current User")) {
                hasPowerPointDoc = true;
            } else if (rawUtf16.contains("WordDocument") || rawUtf16.contains("1Table") || rawUtf16.contains("0Table")) {
                hasWordDoc = true;
            } else if (rawUtf16.contains("Workbook")) {
                hasExcelWorkbook = true;
            }
        }

        LegacyKind kind = LegacyKind.OTHER_OLE2;
        if (hasWordDoc) {
            kind = LegacyKind.WORD_DOC;
        } else if (hasPowerPointDoc) {
            kind = LegacyKind.POWERPOINT_PPT;
        } else if (hasExcelWorkbook) {
            kind = LegacyKind.EXCEL_XLS;
        }

        if (targetStream != null && targetStream.size > 0 && targetStream.startSector >= 0) {
            byte[] streamBytes = readStream(data, fat, sectorSize, targetStream.startSector, (int) Math.min(targetStream.size, 4 * 1024 * 1024));
            if (kind == LegacyKind.POWERPOINT_PPT) {
                paragraphs.addAll(extractPowerPointTextRecords(streamBytes));
            }
            if (paragraphs.isEmpty()) {
                paragraphs.addAll(extractReadableTextRuns(streamBytes));
            }
        }

        if (paragraphs.isEmpty()) {
            if (kind == LegacyKind.POWERPOINT_PPT) {
                paragraphs.addAll(extractPowerPointTextRecords(data));
            }
            if (paragraphs.isEmpty()) {
                paragraphs.addAll(extractReadableTextRuns(data));
            }
        }

        return new InspectionResult(kind, streams, paragraphs);
    }

    private static List<String> extractPlainOrRtfOrXmlParagraphs(byte[] data) {
        List<String> out = new ArrayList<>();
        String utf8 = new String(data, 0, Math.min(data.length, 2 * 1024 * 1024), StandardCharsets.UTF_8);
        String trimmed = utf8.trim();
        if (trimmed.startsWith("{\\rtf")) {
            String cleaned = trimmed
                    .replaceAll("\\\\par\\b", "\n")
                    .replaceAll("\\\\line\\b", "\n")
                    .replaceAll("\\\\'[0-9a-fA-F]{2}", " ")
                    .replaceAll("\\\\[a-zA-Z]+-?\\d*\\s?", "")
                    .replaceAll("[{}]", "");
            for (String line : cleaned.split("\\r?\\n")) {
                String s = line.trim();
                if (isMeaningfulText(s)) out.add(s);
            }
            if (!out.isEmpty()) return out;
        }
        if (trimmed.startsWith("<")) {
            String cleaned = trimmed
                    .replaceAll("(?i)</(p|div|tr|li|h[1-6]|w:p)>", "\n")
                    .replaceAll("<[^>]+>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
            for (String line : cleaned.split("\\r?\\n")) {
                String s = line.replaceAll("\\s+", " ").trim();
                if (isMeaningfulText(s)) out.add(s);
            }
            if (!out.isEmpty()) return out;
        }
        for (String line : utf8.split("\\r?\\n")) {
            String s = line.trim();
            if (isMeaningfulText(s)) out.add(s);
        }
        if (out.isEmpty()) {
            out.addAll(extractReadableTextRuns(data));
        }
        return out;
    }

    private static int[] readFat(byte[] data, ByteBuffer hdr, int sectorSize, int numFatSectors) {
        List<Integer> fatSectors = new ArrayList<>();
        int maxDifat = Math.min(109, numFatSectors);
        for (int i = 0; i < maxDifat; i++) {
            int sec = hdr.getInt(76 + i * 4);
            if (sec >= 0 && sec != FREESECT && sec != ENDOFCHAIN) {
                fatSectors.add(sec);
            }
        }
        int intsPerSector = sectorSize / 4;
        int[] fat = new int[fatSectors.size() * intsPerSector];
        int outIdx = 0;
        for (int sec : fatSectors) {
            int offset = (sec + 1) * sectorSize;
            if (offset < 0 || offset + sectorSize > data.length) continue;
            ByteBuffer sb = ByteBuffer.wrap(data, offset, sectorSize).slice().order(ByteOrder.LITTLE_ENDIAN);
            for (int j = 0; j < intsPerSector; j++) {
                fat[outIdx++] = sb.getInt(j * 4);
            }
        }
        return fat;
    }

    private static final class DirEntry {
        final String name;
        final int type;
        final int startSector;
        final long size;

        DirEntry(String name, int type, int startSector, long size) {
            this.name = name;
            this.type = type;
            this.startSector = startSector;
            this.size = size;
        }
    }

    private static List<DirEntry> readDirectoryEntries(byte[] data, int[] fat, int sectorSize, int firstDirSector) {
        List<DirEntry> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        int sec = firstDirSector;
        int steps = 0;
        while (sec >= 0 && sec != ENDOFCHAIN && sec != FREESECT && visited.add(sec) && steps++ < 512) {
            int offset = (sec + 1) * sectorSize;
            if (offset < 0 || offset + sectorSize > data.length) break;
            for (int pos = offset; pos + 128 <= offset + sectorSize; pos += 128) {
                int nameLen = ((data[pos + 64] & 0xFF) | ((data[pos + 65] & 0xFF) << 8));
                int type = data[pos + 66] & 0xFF;
                if (type == 0 || nameLen < 2 || nameLen > 64) continue;
                String name = new String(data, pos, nameLen - 2, StandardCharsets.UTF_16LE);
                ByteBuffer eb = ByteBuffer.wrap(data, pos, 128).slice().order(ByteOrder.LITTLE_ENDIAN);
                int startSec = eb.getInt(116);
                long size = eb.getInt(120) & 0xFFFFFFFFL;
                result.add(new DirEntry(name, type, startSec, size));
            }
            sec = (sec < fat.length) ? fat[sec] : ENDOFCHAIN;
        }
        return result;
    }

    private static byte[] readStream(byte[] data, int[] fat, int sectorSize, int startSector, int maxLen) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Set<Integer> visited = new HashSet<>();
        int sec = startSector;
        while (sec >= 0 && sec != ENDOFCHAIN && sec != FREESECT && visited.add(sec) && out.size() < maxLen) {
            int offset = (sec + 1) * sectorSize;
            if (offset < 0 || offset + sectorSize > data.length) break;
            int toWrite = Math.min(sectorSize, maxLen - out.size());
            out.write(data, offset, toWrite);
            sec = (sec < fat.length) ? fat[sec] : ENDOFCHAIN;
        }
        return out.toByteArray();
    }

    /**
     * Extracts PPT TextCharsAtom (0x0FA0) and TextBytesAtom (0x0FA8) records.
     */
    private static List<String> extractPowerPointTextRecords(byte[] stream) {
        List<String> list = new ArrayList<>();
        if (stream == null || stream.length < 8) return list;
        ByteBuffer bb = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN);
        int i = 0;
        while (i + 8 <= stream.length) {
            int verInst = bb.getShort(i) & 0xFFFF;
            int recType = bb.getShort(i + 2) & 0xFFFF;
            int recLen = bb.getInt(i + 4);
            boolean isContainer = (verInst & 0x0F) == 0x0F;

            if (recLen >= 0 && i + 8 + recLen <= stream.length) {
                if (recType == 0x0FA0 && recLen >= 2) { // TextCharsAtom (UTF-16LE)
                    String text = new String(stream, i + 8, recLen, StandardCharsets.UTF_16LE)
                            .replace('\r', '\n')
                            .trim();
                    if (isMeaningfulText(text)) list.add(text);
                    i += 8 + recLen;
                    continue;
                } else if (recType == 0x0FA8 && recLen >= 2) { // TextBytesAtom (ANSI/CP1252)
                    String text = new String(stream, i + 8, recLen, Charset.forName("windows-1252"))
                            .replace('\r', '\n')
                            .trim();
                    if (isMeaningfulText(text)) list.add(text);
                    i += 8 + recLen;
                    continue;
                } else if (isContainer) {
                    i += 8;
                    continue;
                }
            }
            i += 2;
        }
        return list;
    }

    private static List<String> extractReadableTextRuns(byte[] bytes) {
        List<String> runs = new ArrayList<>();
        if (bytes == null || bytes.length < 4) return runs;

        // Scan UTF-16LE runs (supports both Latin and Arabic Unicode blocks)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            char ch = (char) ((bytes[i] & 0xFF) | ((bytes[i + 1] & 0xFF) << 8));
            if (isAllowedChar(ch)) {
                sb.append(ch);
            } else {
                flushRun(sb, runs);
                if (runs.size() >= 400) break;
            }
        }
        flushRun(sb, runs);

        if (runs.isEmpty()) {
            // Fallback ASCII / Latin scan
            for (byte b : bytes) {
                int c = b & 0xFF;
                if ((c >= 32 && c <= 126) || c == '\n' || c == '\r') {
                    sb.append(c == '\r' ? '\n' : (char) c);
                } else {
                    flushRun(sb, runs);
                    if (runs.size() >= 300) break;
                }
            }
            flushRun(sb, runs);
        }
        return runs;
    }

    private static void flushRun(StringBuilder sb, List<String> out) {
        if (sb.length() == 0) return;
        String text = sb.toString().trim();
        sb.setLength(0);
        if (isMeaningfulText(text)) {
            out.add(text);
        }
    }

    private static boolean isAllowedChar(char ch) {
        if (ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') return true;
        if (ch >= 0x0021 && ch <= 0x007E) return true;
        if (ch >= 0x0600 && ch <= 0x06FF) return true; // Arabic
        if (ch >= 0x0750 && ch <= 0x077F) return true; // Arabic Supplement
        if (ch >= 0xFB50 && ch <= 0xFDFF) return true; // Arabic Presentation Forms-A
        if (ch >= 0xFE70 && ch <= 0xFEFF) return true; // Arabic Presentation Forms-B
        return Character.isLetterOrDigit(ch);
    }

    private static boolean isMeaningfulText(String s) {
        if (s == null || s.length() < 3) return false;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) letters++;
        }
        return letters >= 2;
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
            int remaining = maxBytes - out.size();
            if (remaining <= 0) break;
            out.write(buf, 0, Math.min(read, remaining));
        }
        return out.toByteArray();
    }
}
