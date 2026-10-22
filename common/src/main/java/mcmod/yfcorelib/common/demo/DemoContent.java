package mcmod.yfcorelib.common.demo;

import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.common.registry.ContentNamespace;
import mcmod.yfcorelib.common.registry.YFRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开发环境演示内容：注册一对极简物品/方块，作为注册 DSL 的常驻运行时验证载体。
 * <p>仅 dev 注册，发布环境零影响；不提供贴图与模型，也不进创造模式物品栏。</p>
 */
public final class DemoContent {
	public static final String ITEM_PATH = "demo_item";
	public static final String BLOCK_PATH = "demo_block";

	private static final Logger LOG = LoggerFactory.getLogger(DemoContent.class);
	private static volatile boolean registered;

	private DemoContent() {
	}

	/** 幂等注册；Forge 需在模组构造期调用以创建延迟注册表 */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		if (!YFCoreLib.platform().isDevelopment()) {
			return;
		}
		ContentNamespace namespace = YFRegistries.namespace(YFCoreLib.MOD_ID);
		namespace.items().add(ITEM_PATH, () -> new Item(new Item.Properties()));
		namespace.block(BLOCK_PATH, Block::new, BlockBehaviour.Properties.of());
		LOG.info("演示内容已注册（仅开发环境）: {}:{} / {}:{}",
				YFCoreLib.MOD_ID, ITEM_PATH, YFCoreLib.MOD_ID, BLOCK_PATH);
	}
}
