package mcmod.yfcorelib.api.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** TextLayout：包围盒计算与参数校验（用假度量，不碰游戏） */
class TextLayoutTest {
	/** 每字符 6 像素、行高 9 像素；折行按"每 3 个字符一行"模拟 */
	private static final class FakeMeasure implements TextMeasure {
		final List<Integer> wrapCalls = new ArrayList<>();
		int lastMaxWidth;

		@Override
		public int width(String text) {
			return text.length() * 6;
		}

		@Override
		public int lineHeight() {
			return 9;
		}

		@Override
		public List<String> wrap(String text, int maxWidth) {
			wrapCalls.add(maxWidth);
			lastMaxWidth = maxWidth;
			if (text.isEmpty()) {
				return List.of();
			}
			List<String> lines = new ArrayList<>();
			for (int i = 0; i < text.length(); i += 3) {
				lines.add(text.substring(i, Math.min(text.length(), i + 3)));
			}
			return lines;
		}
	}

	@Test
	void shouldComputeBoundingBoxFromLines() {
		TextLayout layout = TextLayout.of(new FakeMeasure(), List.of("abc", "de"));
		assertEquals(2, layout.lineCount());
		assertEquals(18, layout.width());
		assertEquals(18, layout.height());
	}

	@Test
	void wrapOverloadShouldDelegateToMeasure() {
		FakeMeasure measure = new FakeMeasure();
		TextLayout layout = TextLayout.of(measure, "abcdefgh", 40);
		assertEquals(List.of(40), measure.wrapCalls);
		assertEquals(List.of("abc", "def", "gh"), layout.lines());
		assertEquals(3, layout.lineCount());
		assertEquals(18, layout.width());
		assertEquals(27, layout.height());
	}

	@Test
	void emptyTextShouldGiveEmptyLayout() {
		TextLayout layout = TextLayout.of(new FakeMeasure(), "", 100);
		assertTrue(layout.isEmpty());
		assertEquals(0, layout.width());
		assertEquals(0, layout.height());
	}

	@Test
	void nonPositiveMaxWidthShouldThrow() {
		assertThrows(IllegalArgumentException.class,
				() -> TextLayout.of(new FakeMeasure(), "abc", 0));
		assertThrows(IllegalArgumentException.class,
				() -> TextLayout.of(new FakeMeasure(), "abc", -5));
	}

	@Test
	void nullArgumentsShouldThrow() {
		assertThrows(NullPointerException.class, () -> TextLayout.of(null, "abc", 10));
		assertThrows(NullPointerException.class, () -> TextLayout.of(new FakeMeasure(), (String) null, 10));
		assertThrows(NullPointerException.class, () -> TextLayout.of(new FakeMeasure(), (List<String>) null));
	}

	@Test
	void linesShouldBeImmutableCopy() {
		List<String> source = new ArrayList<>(List.of("a"));
		TextLayout layout = TextLayout.of(new FakeMeasure(), source);
		source.add("b");
		assertEquals(1, layout.lineCount());
		assertThrows(UnsupportedOperationException.class, () -> layout.lines().add("c"));
	}

	@Test
	void negativeDimensionsShouldBeRejected() {
		assertThrows(IllegalArgumentException.class, () -> new TextLayout(List.of(), -1, 0));
		assertThrows(IllegalArgumentException.class, () -> new TextLayout(List.of(), 0, -1));
	}
}
