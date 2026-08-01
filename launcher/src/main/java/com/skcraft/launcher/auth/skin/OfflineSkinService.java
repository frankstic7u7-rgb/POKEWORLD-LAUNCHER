package com.skcraft.launcher.auth.skin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skcraft.launcher.util.HttpRequest;
import lombok.extern.java.Log;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;

/**
 * Looks up the real Mojang skin for an arbitrary username, for use with offline
 * (username-only) sessions. Unlike {@link MinecraftSkinService}, this doesn't
 * require an authenticated profile -- it uses Mojang's public, unauthenticated
 * lookup APIs, the same way sites like namemc.com show a skin for any username.
 */
@Log
public class OfflineSkinService {

    private static final String UUID_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/%s";
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s";

    /**
     * @return the rendered 32x32 head PNG bytes, or null if the username doesn't
     * belong to a real Mojang account (or has no skin / the lookup failed) -- the
     * caller should fall back to the default skin icon in that case.
     */
    public static byte[] fetchSkinHeadByUsername(String username) {
        try {
            JsonNode profileLookup = HttpRequest.get(HttpRequest.url(String.format(UUID_LOOKUP_URL, username)))
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asJson(JsonNode.class);

            String uuid = profileLookup.get("id").asText();

            JsonNode profile = HttpRequest.get(HttpRequest.url(String.format(PROFILE_URL, uuid)))
                    .execute()
                    .expectResponseCode(200)
                    .returnContent()
                    .asJson(JsonNode.class);

            for (JsonNode property : profile.get("properties")) {
                if (!"textures".equals(property.path("name").asText())) continue;

                String decoded = new String(
                        Base64.getDecoder().decode(property.get("value").asText()), StandardCharsets.UTF_8);
                JsonNode textures = new ObjectMapper().readTree(decoded);
                String skinUrl = textures.path("textures").path("SKIN").path("url").asText(null);

                if (skinUrl == null) return null;

                return SkinProcessor.renderHead(MinecraftSkinService.downloadSkin(skinUrl));
            }

            return null;
        } catch (Exception e) {
            // Nombre inventado, cuenta sin skin, o fallo de red -- el caller cae al icono default.
            log.log(Level.FINE, "No se pudo obtener la skin real para " + username, e);
            return null;
        }
    }
}
