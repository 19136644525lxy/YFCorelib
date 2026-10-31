package mcmod.yfcorelib.api.mixin;

/**
 * 单测用的假 Mixin 类：{@link MixinConfigValidator} 只校验"类是否存在"，
 * 不需要真实的 {@code @Mixin} 注解，也不需要进游戏。
 */
final class FakeMixin {
	private FakeMixin() {
	}
}
