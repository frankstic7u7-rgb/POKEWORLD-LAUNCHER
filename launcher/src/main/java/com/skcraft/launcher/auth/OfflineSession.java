/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.auth;

import com.skcraft.launcher.auth.skin.OfflineSkinService;
import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * An offline session.
 */
public class OfflineSession implements Session {

    private static Map<String, String> dummyProperties = Collections.emptyMap();

    @Getter
    private final String name;

    // Cacheado en el primer pedido -- el lookup de skin pega a la red, no queremos
    // repetirlo cada vez que Swing repinta la lista de cuentas.
    private byte[] avatarImage;
    private boolean avatarFetched;

    /**
     * Create a new offline session using the given player name.
     *
     * @param name the player name
     */
    public OfflineSession(@NonNull String name) {
        this.name = name;
    }

    @Override
    public String getUuid() {
        // Mismo algoritmo que usa el propio server en modo offline (Spigot/Paper/Arclight)
        // para generar el UUID de un jugador sin cuenta premium -- si no coincidiera, cada
        // jugador offline terminaria con un UUID distinto en el cliente vs. en el server.
        String seed = "OfflinePlayer:" + name;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public String getAccessToken() {
        return "0";
    }

    @Override
    public Map<String, String> getUserProperties() {
        return dummyProperties;
    }

    @Override
    public String getSessionToken() {
        return "-";
    }

    @Override
    public UserType getUserType() {
        return UserType.LEGACY;
    }

    @Override
    public byte[] getAvatarImage() {
        // Pega a la red la primera vez que se pide (bloqueante) -- quien construye la
        // sesion (AccountSelectDialog.attemptOfflineLogin) lo hace dentro de un ProgressDialog, no en el EDT
        // directo, asi que esto no cuelga la interfaz.
        if (!avatarFetched) {
            avatarImage = OfflineSkinService.fetchSkinHeadByUsername(name);
            avatarFetched = true;
        }
        return avatarImage;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

}
