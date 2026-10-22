package mcmod.yfcorelib.common.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** 平台注册后端 SPI：由 fabric/forge 模块实现并注册到 META-INF/services */
public interface RegistrarFactory {
	<T> Registrar<T> create(String modId, ResourceKey<? extends Registry<T>> key);
}
