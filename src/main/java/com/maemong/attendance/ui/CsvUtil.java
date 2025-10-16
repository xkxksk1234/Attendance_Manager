package com.maemong.attendance.ui;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class CsvUtil {
	private CsvUtil() {}

	/** ✅ UTF-8 with BOM Writer (엑셀 한글 깨짐 방지) */
	public static Writer newUtf8BomWriter(File file) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		// UTF-8 BOM: EF BB BF
		fos.write(0xEF);
		fos.write(0xBB);
		fos.write(0xBF);
		return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
	}

	public static void writeLine(Appendable out, String[] cells) throws IOException {
		boolean first = true;
		for (String cell : cells) {
			if (!first) out.append(',');
			out.append(escape(cell == null ? "" : cell));
			first = false;
		}
		out.append('\n');
	}

	private static String escape(String s) {
		boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
		String v = s.replace("\"", "\"\"");
		return needQuote ? "\"" + v + "\"" : v;
	}
}
