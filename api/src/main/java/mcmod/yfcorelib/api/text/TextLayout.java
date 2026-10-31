package mcmod.yfcorelib.api.text;

import java.util.List;
import java.util.Objects;

/**
 * 折行后的布局结果：每一行文本 + 整体包围盒。
 * <p>纯数据，方便 HUD 侧直接用来算居中、背景框尺寸。</p>
 *
 * @param lines  折行结果，保持从上到下的顺序
 * @param width  所有行里的最大宽度（像素）
 * @param height 总高度 = 行数 × 行高（像素）
 */
public record TextLayout(List<String> lines, int width, int height) {
	public TextLayout {
		Objects.requireNonNull(lines, "lines");
		lines = List.copyOf(lines);
		if (width < 0) {
			throw new IllegalArgumentException("width 不得为负: " + width);
		}
		if (height < 0) {
			throw new IllegalArgumentException("height 不得为负: " + height);
		}
	}

	/**
	 * 先按宽度折行，再按度量结果算包围盒。
	 *
	 * @param measure  文本度量
	 * @param text     原文
	 * @param maxWidth 最大宽度，必须为正
	 * @throws IllegalArgumentException maxWidth 不为正
	 */
	public static TextLayout of(TextMeasure measure, String text, int maxWidth) {
		Objects.requireNonNull(measure, "measure");
		Objects.requireNonNull(text, "text");
		if (maxWidth <= 0) {
			throw new IllegalArgumentException("maxWidth 必须为正: " + maxWidth);
		}
		return of(measure, measure.wrap(text, maxWidth));
	}

	/**
	 * 用已知的行列表算包围盒（不折行）。
	 *
	 * @param measure 文本度量
	 * @param lines   每行文本
	 */
	public static TextLayout of(TextMeasure measure, List<String> lines) {
		Objects.requireNonNull(measure, "measure");
		Objects.requireNonNull(lines, "lines");
		int maxLineWidth = 0;
		for (String line : lines) {
			maxLineWidth = Math.max(maxLineWidth, measure.width(line));
		}
		return new TextLayout(lines, maxLineWidth, lines.size() * measure.lineHeight());
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public int lineCount() {
		return lines.size();
	}
}
