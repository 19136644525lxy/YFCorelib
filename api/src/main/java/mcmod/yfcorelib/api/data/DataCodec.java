package mcmod.yfcorelib.api.data;

import com.google.gson.JsonElement;

/**
 * 业务数据编解码器：业务对象 <-> JSON 元素。
 * <p>decode 失败应抛 RuntimeException，由存储层捕获并跳过损坏条目。</p>
 */
public interface DataCodec<T> {
	JsonElement encode(T value);

	T decode(JsonElement json);
}
