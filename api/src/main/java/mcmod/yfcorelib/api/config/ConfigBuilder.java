package mcmod.yfcorelib.api.config;

/** 配置 DSL：链式定义键、注释与版本，最终产出不可变 ConfigSpec */
public final class ConfigBuilder {
	private final SimpleConfigSpec.Builder builder;

	private ConfigBuilder(SimpleConfigSpec.Builder builder) {
		this.builder = builder;
	}

	public static ConfigBuilder create(String name) {
		return new ConfigBuilder(new SimpleConfigSpec.Builder(name));
	}

	public <T> ConfigBuilder define(String path, Class<T> type, T defaultValue) {
		builder.define(path, type, defaultValue);
		return this;
	}

	/**
	 * 装入一个已经定义好的键（含注释）。
	 * <p>业务侧通常先把键声明成 {@code public static final ConfigKey<T>} 常量，
	 * 再用本方法装进 spec——这样读配置时用的是常量，不必按路径字符串反查。</p>
	 */
	public <T> ConfigBuilder define(ConfigKey<T> key) {
		builder.define(key);
		return this;
	}

	public ConfigBuilder comment(String path, String comment) {
		builder.comment(path, comment);
		return this;
	}

	public ConfigBuilder version(int version) {
		builder.version(version);
		return this;
	}

	public ConfigSpec build() {
		return builder.build();
	}
}
