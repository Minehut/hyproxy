package ac.eva.hyproxy.io.packet.impl.auth;

import io.netty.buffer.ByteBuf;
import ac.eva.hyproxy.io.HytalePacketHandler;
import ac.eva.hyproxy.io.packet.Packet;

public class RequestInsecurePlayerOptions implements Packet {

    public static RequestInsecurePlayerOptions deserialize(ByteBuf buf) {
        return new RequestInsecurePlayerOptions();
    }

    @Override
    public boolean handle(HytalePacketHandler handler) {
        return handler.handle(this);
    }

    @Override
    public void serialize(ByteBuf buf) {
        // empty payload
    }
}
