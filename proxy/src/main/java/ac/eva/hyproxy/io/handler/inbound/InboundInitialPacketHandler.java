package ac.eva.hyproxy.io.handler.inbound;

import ac.eva.hyproxy.auth.JWTVerifier;
import ac.eva.hyproxy.io.packet.impl.ClientDisconnect;
import io.netty.buffer.Unpooled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ac.eva.hyproxy.backend.HyProxyBackend;
import ac.eva.hyproxy.common.util.SecretMessageUtil;
import ac.eva.hyproxy.event.impl.player.PlayerPreAuthConnectEvent;
import ac.eva.hyproxy.io.HytaleConnection;
import ac.eva.hyproxy.io.HytalePacketHandler;
import ac.eva.hyproxy.io.packet.impl.auth.Connect;
import ac.eva.hyproxy.io.proto.PlayerSkin;
import ac.eva.hyproxy.player.HyProxyPlayer;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class InboundInitialPacketHandler implements HytalePacketHandler {
    private final HytaleConnection connection;

    @Override
    public boolean handle(Connect connect) {
        if (connect.getClientType() == null) {
            connection.disconnect("invalid client type");
            return true;
        }

        String identityToken = connect.getIdentityToken();
        if (identityToken == null) {
            connection.disconnect("This proxy only supports online mode players!");
            return true;
        }

        JWTVerifier.IdentityTokenClaims claims = connection.getProxy().getJwtVerifier().validateIdentityToken(identityToken);
        if (claims == null) {
            connection.disconnect("invalid or expired identity token");
            return true;
        }

        UUID profileId = claims.getSubjectAsUUID();
        if (profileId == null) {
            connection.disconnect("invalid identity token: missing or malformed subject");
            return true;
        }

        if (connection.getProxy().getPlayerByProfileId(profileId) != null) {
            connection.disconnect("You are already connected to this proxy!");
            return true;
        }

        HyProxyPlayer player = new HyProxyPlayer(connection.getProxy(), connection);

        player.setProtocolCrc(connect.getProtocolCrc());
        player.setProtocolBuildNumber(connect.getProtocolBuildNumber());
        player.setClientVersion(connect.getClientVersion());
        player.setProfileId(profileId);
        player.setIdentityToken(identityToken);
        player.setLanguage(connect.getLanguage());
        player.setClientType(connect.getClientType());
        player.setSkin(PlayerSkin.fromJson(claims.skin()));

        byte[] referralData = connect.getReferralData();
        if (referralData != null) {
            SecretMessageUtil.BackendReferralMessage referralMessage = SecretMessageUtil.validateAndDecodeReferralData(
                    Unpooled.copiedBuffer(referralData),
                    profileId,
                    connection.getProxy().getConfiguration().getProxySecret()
            );

            if (referralMessage == null) {
                connection.disconnect("invalid referral data");
                return true;
            }

            HyProxyBackend backend = connection.getProxy().getBackendById(referralMessage.backendId());

            if (backend == null) {
                connection.disconnect("invalid referral backend");
                return true;
            }

            player.setReferredBackend(backend);
        }

        PlayerPreAuthConnectEvent event = connection.getProxy().getEventBus().fire(new PlayerPreAuthConnectEvent(
                player,
                false
        ));

        if (event.isCanceled()) {
            if (connection.isDisconnected()) {
                return true;
            }

            connection.disconnect("proxy pre-auth connect event cancelled without disconnect");
            return true;
        }

        if (connection.isDisconnected()) {
            return true;
        }

        connection.setPlayer(player);

        log.info("authenticating player {}", this.connection.getIdentifier());
        connection.setPacketHandler(new InboundAuthPacketHandler(this.connection));
        return true;
    }

    @Override
    public boolean handle(ClientDisconnect serverDisconnect) {
        log.info("{} {}ed: {}", this.connection.getIdentifier(), serverDisconnect.getType().name().toLowerCase(Locale.ROOT), serverDisconnect.getReason());
        return true;
    }
}
