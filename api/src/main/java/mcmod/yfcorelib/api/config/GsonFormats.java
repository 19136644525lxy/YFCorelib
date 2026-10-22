package mcmod.yfcorelib.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/** Gson 输出格式统一入口：美化 + 关闭 HTML 转义，便于人工编辑 */
final class GsonFormats {
	private static final Gson PRETTY = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private GsonFormats() {
	}

	static String pretty(JsonElement element) {
		return PRETTY.toJson(element);
	}
}
