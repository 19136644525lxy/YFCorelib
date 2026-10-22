package mcmod.yfcorelib.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** LifecycleQueue：就绪触发、补发与异常隔离 */
class LifecycleQueueTest {
	private static PlayerRef ref(String name) {
		UUID id = UUID.nameUUIDFromBytes(name.getBytes());
		return new PlayerRef() {
			@Override
			public UUID id() {
				return id;
			}

			@Override
			public String name() {
				return name;
			}

			@Override
			public boolean isOnline() {
				return true;
			}
		};
	}

	@Test
	void playerReadyShouldFireHooks() {
		LifecycleQueue queue = new LifecycleQueue();
		List<String> fired = new ArrayList<>();
		PlayerRef alice = ref("alice");
		queue.addHook(player -> fired.add(player.name()));
		queue.playerReady(alice);
		assertEquals(List.of("alice"), fired);
	}

	@Test
	void lateHookShouldReplayOnlinePlayers() {
		LifecycleQueue queue = new LifecycleQueue();
		List<String> fired = new ArrayList<>();
		PlayerRef alice = ref("alice");
		queue.playerReady(alice);
		queue.addHook(player -> fired.add(player.name()));
		assertEquals(List.of("alice"), fired); // 补发在线玩家
	}

	@Test
	void gonePlayerShouldNotReplay() {
		LifecycleQueue queue = new LifecycleQueue();
		List<String> fired = new ArrayList<>();
		PlayerRef alice = ref("alice");
		queue.playerReady(alice);
		queue.playerGone(alice.id());
		queue.addHook(player -> fired.add(player.name()));
		assertEquals(List.of(), fired);
	}

	@Test
	void rejoinShouldFireAgain() {
		LifecycleQueue queue = new LifecycleQueue();
		List<String> fired = new ArrayList<>();
		PlayerRef alice = ref("alice");
		queue.addHook(player -> fired.add("ready"));
		queue.playerReady(alice);
		queue.playerGone(alice.id());
		queue.playerReady(alice);
		assertEquals(List.of("ready", "ready"), fired);
	}

	@Test
	void hookExceptionShouldNotBreakOthers() {
		LifecycleQueue queue = new LifecycleQueue();
		List<String> fired = new ArrayList<>();
		queue.addHook(player -> {
			throw new RuntimeException("boom");
		});
		queue.addHook(player -> fired.add(player.name()));
		queue.playerReady(ref("bob"));
		assertEquals(List.of("bob"), fired);
	}
}
