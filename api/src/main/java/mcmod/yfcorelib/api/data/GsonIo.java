package mcmod.yfcorelib.api.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

/** data 包统一 Gson 输出入口（与 config 包格式一致：美化 + 不转义 HTML） */
final class GsonIo {
	private static final Gson PRETTY = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private GsonIo() {
	}

	static String toJson(JsonElement element) {
		return PRETTY.toJson(element);
	}
}
