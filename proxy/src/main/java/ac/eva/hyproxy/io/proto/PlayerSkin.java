package ac.eva.hyproxy.io.proto;

import ac.eva.hyproxy.common.util.ProtocolUtil;
import com.nimbusds.jose.util.JSONObjectUtils;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Map;

@Slf4j
public class PlayerSkin {
    private static final int NULL_BITS_SIZE = 3;

    // order is significant: it defines each part's nullBits bit and offset slot, and must match the engine
    private static final String[] PART_KEYS = {
            "bodyCharacteristic", "underwear", "face", "eyes", "ears", "mouth", "facialHair", "haircut",
            "eyebrows", "pants", "overpants", "undertop", "overtop", "shoes", "headAccessory", "faceAccessory",
            "earAccessory", "skinFeature", "gloves", "cape"
    };

    private final @Nullable String[] parts;

    private PlayerSkin(@Nullable String[] parts) {
        this.parts = parts;
    }

    /**
     * Parses the {@code profile.skin} JSON carried in the identity token into a skin.
     * @param json the raw skin json, or null/empty when the player has no skin
     * @return the parsed skin, or null when absent or unparseable
     */
    public static @Nullable PlayerSkin fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        Map<String, Object> skin;
        try {
            skin = JSONObjectUtils.parse(json);
        } catch (ParseException e) {
            log.warn("failed to parse skin json from identity token", e);
            return null;
        }

        String[] parts = new String[PART_KEYS.length];
        for (int i = 0; i < PART_KEYS.length; i++) {
            if (skin.get(PART_KEYS[i]) instanceof String part) {
                parts[i] = part;
            }
        }

        return new PlayerSkin(parts);
    }

    public void serialize(ByteBuf buf) {
        byte[] nullBits = new byte[NULL_BITS_SIZE];
        for (int i = 0; i < this.parts.length; i++) {
            if (this.parts[i] != null) {
                nullBits[i >> 3] |= (byte) (1 << (i & 7));
            }
        }

        buf.writeBytes(nullBits);

        int slotsStart = buf.writerIndex();
        for (int i = 0; i < this.parts.length; i++) {
            buf.writeIntLE(0);
        }

        int varsOffset = buf.writerIndex();
        for (int i = 0; i < this.parts.length; i++) {
            int slot = slotsStart + i * Integer.BYTES;

            if (this.parts[i] == null) {
                buf.setIntLE(slot, -1);
                continue;
            }

            buf.setIntLE(slot, buf.writerIndex() - varsOffset);
            ProtocolUtil.writeVarString(buf, this.parts[i]);
        }
    }

    @Override
    public String toString() {
        return "PlayerSkin" + Arrays.toString(this.parts);
    }
}
