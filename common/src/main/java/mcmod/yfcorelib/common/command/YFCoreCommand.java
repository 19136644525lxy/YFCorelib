package mcmod.yfcorelib.common.command;

import java.util.List;
import java.util.Set;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mcmod.yfcorelib.api.YFCoreLib;
import mcmod.yfcorelib.api.platform.Platform;
import mcmod.yfcorelib.common.diagnostic.SelfTest;
import mcmod.yfcorelib.core.CoreInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * 调试命令：/yfcorelib info | selftest。
 * <p>双端共用同一棵命令树，平台模块只负责挂到各自 dispatcher。</p>
 */
public final class YFCoreCommand {
	private YFCoreCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> builder() {
		// 整棵树是库的排障入口：info 会输出 loader/版本/dev 标志/配置名，
		// 这些不该对普通玩家可见，因此根节点统一要权限 2
		return LiteralArgumentBuilder.<CommandSourceStack>literal("yfcorelib")
				.requires(source -> source.hasPermission(2))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
						.executes(context -> info(context.getSource())))
				.then(LiteralArgumentBuilder.<CommandSourceStack>literal("selftest")
						.executes(context -> selftest(context.getSource())));
	}

	private static int info(CommandSourceStack source) {
		Platform platform = YFCoreLib.platform();
		send(source, CoreInfo.banner());
		send(source, "loader=" + platform.loaderName() + " mc=" + platform.mcVersion()
				+ " loaderVersion=" + platform.loaderVersion() + " dev=" + platform.isDevelopment());
		Set<String> configs = YFCoreLib.configNames();
		send(source, "配置=" + (configs.isEmpty() ? "无" : String.join(",", configs))
				+ " 计时器=" + YFCoreLib.timers().keys().size());
		return 1;
	}

	private static int selftest(CommandSourceStack source) {
		List<SelfTest.Result> results = SelfTest.runAll();
		boolean passed = SelfTest.allPassed(results);
		send(source, "YFCoreLib 自检：" + (passed ? "全部通过" : "存在失败"));
		for (SelfTest.Result result : results) {
			send(source, (result.passed() ? "[PASS] " : "[FAIL] ") + result.name() + " - " + result.detail());
		}
		return passed ? 1 : 0;
	}

	private static void send(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
	}
}
