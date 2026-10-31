package mcmod.yfcorelib.common.text;

import java.util.List;
import java.util.Objects;

import mcmod.yfcorelib.api.text.TextMeasure;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 基于原版 {@link Font} 的 {@link TextMeasure} 实现：折行直接交给 {@code Font#split}，
 * 因此宽度计算与 CJK 断行都与原版一致。
 *
 * <p>注意 {@code Font#split} 返回的是 {@link FormattedCharSequence}（带样式但不可直接取字符串），
 * 而本接口返回 {@code String}，所以这里会把每一行**拍平成纯文本**——样式（颜色、粗体等）会丢失。
 * 需要保留样式时不要走这个接口，直接用 {@code Font#split} 拿 {@code FormattedCharSequence}。
 *
 * <pre>{@code
 * TextMeasure measure = new FontTextMeasure(Minecraft.getInstance().font);
 * TextLayout layout = TextLayout.of(measure, subtitle, 240);
 * }</pre>
 *
 * <p><strong>仅客户端可用</strong>：本类引用了 {@code net.minecraft.client.gui.Font}，
 * 专用服务器上加载会抛错。请只在客户端类（Screen / HUD 渲染路径）里构造它。</p>
 *
 * <p>另外 {@link #wrap} 只按像素宽度折行，不处理原文里的换行符；
 * 需要保留显式换行时，先按 {@code "\n"} 切分，再对每段分别折行。</p>
 */
public final class FontTextMeasure implements TextMeasure {
	private final Font font;

	public FontTextMeasure(Font font) {
		this.font = Objects.requireNonNull(font, "font");
	}

	@Override
	public int width(String text) {
		Objects.requireNonNull(text, "text");
		return font.width(text);
	}

	@Override
	public int lineHeight() {
		return font.lineHeight;
	}

	@Override
	public List<String> wrap(String text, int maxWidth) {
		Objects.requireNonNull(text, "text");
		if (maxWidth <= 0) {
			throw new IllegalArgumentException("maxWidth 必须为正: " + maxWidth);
		}
		if (text.isEmpty()) {
			return List.of();
		}
		return font.split(Component.literal(text), maxWidth).stream()
				.map(FontTextMeasure::toPlainText)
				.toList();
	}

	/** 把带样式的行拍平成纯文本：样式丢弃，只保留字符 */
	private static String toPlainText(FormattedCharSequence sequence) {
		StringBuilder builder = new StringBuilder();
		sequence.accept((index, style, codePoint) -> {
			builder.appendCodePoint(codePoint);
			return true;
		});
		return builder.toString();
	}
}
