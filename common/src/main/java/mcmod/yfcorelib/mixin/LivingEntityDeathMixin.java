package mcmod.yfcorelib.mixin;

import mcmod.yfcorelib.api.event.PlayerDeathEvent;
import mcmod.yfcorelib.api.mixin.MixinCallbacks;
import mcmod.yfcorelib.common.event.PlayerRefs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 本库的第一个、也是唯一一个生产 Mixin：在实体死亡瞬间发事件。
 * <p>注入点选 {@code LivingEntity#die} 的 HEAD——此时玩家血量已归零、但实体尚未被移除，
 * 适合做"死亡瞬间"的结算（掉落、数据落盘、统计等）。</p>
 *
 * <p><strong>Mixin 里不写业务</strong>：这里只做"发现时机 + 组事件"，
 * 具体逻辑一律写成 {@link PlayerDeathEvent} 的监听器，从而可单测、可退订、不会把异常抛进原版方法。</p>
 *
 * <p>没有给事件加可取消能力：{@code die} 已经被原版判定完毕，此时取消只会得到状态不一致的实体。
 * 真需要拦截死亡请在自己的模组里对 {@code hurt} 注入。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void yfcorelib$firePlayerDeath(DamageSource source, CallbackInfo callbackInfo) {
		// 先转 Object 再 instanceof：编译期本类与 ServerPlayer 没有继承关系，
		// 直接写 `this instanceof ServerPlayer` 会被 javac 判为不可转换
		if ((Object) this instanceof ServerPlayer serverPlayer) {
			MixinCallbacks.fire(new PlayerDeathEvent(PlayerRefs.of(serverPlayer)));
		}
	}
}
