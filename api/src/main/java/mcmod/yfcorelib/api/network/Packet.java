package mcmod.yfcorelib.api.network;

/** 网络包标记接口：业务实现需返回所属包类型 id（含命名空间） */
public interface Packet {
	/** 对应 PacketType 的 id，如 "mymod:hello" */
	String typeId();
}
