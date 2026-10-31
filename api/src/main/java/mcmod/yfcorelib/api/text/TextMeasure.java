package mcmod.yfcorelib.api.text;

import java.util.List;

/**
 * 文本度量：把"这段字占多宽、要折几行"抽象出来。
 * <p>接口本身不依赖 Minecraft（api 层不允许出现游戏类型），真正的实现由平台侧提供——
 * 见 {@code common} 模块的 {@code FontTextMeasure}（内部用客户端的 {@code Font}）。</p>
 *
 * <p>之所以值得抽一层：折行是 HUD/字幕/提示框类模组最容易写歪的地方，
 * 而"宽度"这件事既可能来自原版字体，也可能来自自定义位图字体；把它做成接口后，
 * 折行逻辑与布局计算就能脱离游戏单测。</p>
 */
public interface TextMeasure {
	/**
	 * 单行文本的像素宽度。
	 *
	 * @param text 文本，不得为 null（空串返回 0）
	 */
	int width(String text);

	/** 单行行高（像素），通常等于字体行高 */
	int lineHeight();

	/**
	 * 按最大像素宽度折行。
	 * <p>返回的每一行都满足 {@code width(line) <= maxWidth}（除非单个字符本身就超宽，
	 * 此时该行至少包含一个字符，否则会死循环）。</p>
	 *
	 * @param text     原文，不得为 null
	 * @param maxWidth 最大宽度，必须为正
	 * @return 折行结果；原文为空时返回空列表
	 */
	List<String> wrap(String text, int maxWidth);
}
