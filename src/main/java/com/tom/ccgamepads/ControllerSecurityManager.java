package com.tom.ccgamepads;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ControllerSecurityManager {
    private static final Map<UUID, Set<Integer>> PLAYER_CONTROLLERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, String>> CONTROLLER_GUIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, String>> CONTROLLER_NAMES = new ConcurrentHashMap<>();

    public static void replaceControllers(UUID playerId, Map<Integer, ControllerIdentity> controllers) {
        if (controllers.isEmpty()) {
            clearPlayer(playerId);
            return;
        }

        Set<Integer> ids = new CopyOnWriteArraySet<>(controllers.keySet());
        Map<Integer, String> guids = new ConcurrentHashMap<>();
        Map<Integer, String> names = new ConcurrentHashMap<>();
        controllers.forEach((id, identity) -> {
            guids.put(id, identity.guid());
            names.put(id, identity.name());
        });
        PLAYER_CONTROLLERS.put(playerId, ids);
        CONTROLLER_GUIDS.put(playerId, guids);
        CONTROLLER_NAMES.put(playerId, names);
    }

    public static void registerController(UUID playerId, int controllerId, String name, String guid) {
        PLAYER_CONTROLLERS.computeIfAbsent(playerId, ignored -> new CopyOnWriteArraySet<>()).add(controllerId);
        CONTROLLER_GUIDS.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(controllerId, guid);
        CONTROLLER_NAMES.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(controllerId, name);
    }

    public static boolean verifyController(UUID playerId, int controllerId, String guid) {
        Set<Integer> controllers = PLAYER_CONTROLLERS.get(playerId);
        if (controllers == null || !controllers.contains(controllerId)) return false;
        String registeredGuid = CONTROLLER_GUIDS.getOrDefault(playerId, Map.of()).get(controllerId);
        return guid != null && guid.equals(registeredGuid);
    }

    public static String getControllerName(UUID playerId, int controllerId) {
        return CONTROLLER_NAMES.getOrDefault(playerId, Map.of()).getOrDefault(controllerId, "Gamepad");
    }

    public static void clearPlayer(UUID playerId) {
        PLAYER_CONTROLLERS.remove(playerId);
        CONTROLLER_GUIDS.remove(playerId);
        CONTROLLER_NAMES.remove(playerId);
    }

    public record ControllerIdentity(String name, String guid) {}
}
