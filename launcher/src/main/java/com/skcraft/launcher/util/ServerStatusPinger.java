package com.skcraft.launcher.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.java.Log;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.logging.Level;

/**
 * Consulta el estado (jugadores online) de un server de Minecraft usando el
 * protocolo de "Server List Ping" -- el mismo que usa el cliente vanilla para
 * mostrar el ping/cantidad de jugadores en la lista de multijugador, sin
 * necesitar unirse. Resuelve el registro SRV primero (necesario para hosts
 * tipo joinmc.link que enrutan por nombre, no por el puerto por defecto).
 */
@Log
public class ServerStatusPinger {

    @Data
    public static class Status {
        private final int online;
        private final int max;
    }

    public static Status ping(String host, int port, int timeoutMs) {
        try {
            InetSocketAddress resolved = resolveSrv(host, port);
            try (Socket socket = new Socket()) {
                socket.connect(resolved, timeoutMs);
                socket.setSoTimeout(timeoutMs);

                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Handshake packet (id 0x00): protocol version, server address, port, next state (1 = status)
                ByteArrayOutputStream handshakeData = new ByteArrayOutputStream();
                DataOutputStream handshake = new DataOutputStream(handshakeData);
                handshake.writeByte(0x00);
                writeVarInt(handshake, 47); // version no importa para status, cualquier valor sirve
                writeString(handshake, resolved.getHostString());
                handshake.writeShort(resolved.getPort());
                writeVarInt(handshake, 1);
                writePacket(out, handshakeData.toByteArray());

                // Status request packet (id 0x00, sin payload)
                writePacket(out, new byte[]{0x00});

                // Leer status response packet
                readVarInt(in); // largo total del packet, no lo necesitamos
                int packetId = readVarInt(in);
                if (packetId != 0x00) {
                    throw new IOException("Respuesta inesperada del server (packet id " + packetId + ")");
                }
                int jsonLength = readVarInt(in);
                byte[] jsonBytes = new byte[jsonLength];
                in.readFully(jsonBytes);
                String json = new String(jsonBytes, StandardCharsets.UTF_8);

                JsonNode root = new ObjectMapper().readTree(json);
                JsonNode players = root.path("players");
                int online = players.path("online").asInt(-1);
                int max = players.path("max").asInt(-1);
                return new Status(online, max);
            }
        } catch (Exception e) {
            log.log(Level.FINE, "No se pudo consultar el estado del server " + host + ":" + port, e);
            return null;
        }
    }

    /**
     * Busca el registro SRV de _minecraft._tcp.<host> -- si existe, ese es el
     * host/puerto real a usar (asi funcionan los hosts tipo joinmc.link).
     * Si no hay SRV, se usa el host/puerto tal cual se paso.
     */
    private static InetSocketAddress resolveSrv(String host, int defaultPort) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            Attribute srv = attrs.get("SRV");
            if (srv != null && srv.size() > 0) {
                String[] parts = ((String) srv.get(0)).split(" ");
                if (parts.length == 4) {
                    int port = Integer.parseInt(parts[2]);
                    String target = parts[3].replaceAll("\\.$", "");
                    return new InetSocketAddress(target, port);
                }
            }
        } catch (Exception e) {
            // Sin SRV, seguimos con el host/puerto directo
        }
        return new InetSocketAddress(host, defaultPort);
    }

    private static void writePacket(DataOutputStream out, byte[] data) throws IOException {
        ByteArrayOutputStream lengthPrefixed = new ByteArrayOutputStream();
        DataOutputStream lengthOut = new DataOutputStream(lengthPrefixed);
        writeVarInt(lengthOut, data.length);
        out.write(lengthPrefixed.toByteArray());
        out.write(data);
        out.flush();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new IOException("VarInt demasiado largo");
        }
        return value;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
