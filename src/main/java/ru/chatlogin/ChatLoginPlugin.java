package ru.chatlogin;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatLoginPlugin extends JavaPlugin implements Listener {

    private enum AuthState {
        WAITING_PASSWORD, // новый игрок — придумывает пароль
        WAITING_LOGIN     // зарегистрированный — вводит пароль
    }

    private final Map<UUID, AuthState> authStates = new HashMap<>();

    private AuthMeApi authMeApi;

    @Override
    public void onEnable() {
        authMeApi = AuthMeApi.getInstance();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ChatLogin загружен!");
    }

    @Override
    public void onDisable() {
        authStates.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            if (authMeApi.isAuthenticated(player)) return;

            if (authMeApi.isRegistered(player.getName())) {
                authStates.put(uuid, AuthState.WAITING_LOGIN);
                player.sendMessage("§eДобро пожаловать! Введите ваш пароль в чат:");
            } else {
                authStates.put(uuid, AuthState.WAITING_PASSWORD);
                player.sendMessage("§aДобро пожаловать! Придумайте пароль и введите в чат:");
                player.sendMessage("§7(минимум 8 символов)");
            }
        }, 20L);
    }

    // HIGHEST + ignoreCancelled=false — наш обработчик будет последним,
    // гарантированно отменит сообщение даже если другой плагин его уже обработал
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!authStates.containsKey(uuid)) return;

        // Отменяем сообщение — пароль не виден в чате
        event.setCancelled(true);
        // Очищаем получателей на случай если другой плагин уже добавил их
        event.getRecipients().clear();

        String input = event.getMessage().trim();
        AuthState state = authStates.get(uuid);

        switch (state) {
            case WAITING_PASSWORD -> handleNewPassword(player, input);
            case WAITING_LOGIN    -> handleLogin(player, input);
        }
    }

    private void handleNewPassword(Player player, String password) {
        if (password.length() < 8) {
            player.sendMessage("§cПароль слишком короткий! Минимум 8 символов. Попробуйте снова:");
            return;
        }

        UUID uuid = player.getUniqueId();

        // Регистрируем напрямую через AuthMe API — без шага подтверждения,
        // потому что AuthMe сам не ждёт второго сообщения через API
        Bukkit.getScheduler().runTask(this, () -> {
            boolean success = authMeApi.registerPlayer(player.getName(), password);
            if (success) {
                authStates.remove(uuid);
                player.sendMessage("§aВы успешно зарегистрированы! Запомните ваш пароль.");
            } else {
                player.sendMessage("§cОшибка регистрации. Попробуйте снова:");
            }
        });
    }

    private void handleLogin(Player player, String password) {
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTask(this, () -> {
            if (authMeApi.checkPassword(player.getName(), password)) {
                authMeApi.forceLogin(player);
                authStates.remove(uuid);
                player.sendMessage("§aВы успешно вошли!");
            } else {
                player.sendMessage("§cНеверный пароль! Попробуйте ещё раз:");
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        authStates.remove(event.getPlayer().getUniqueId());
    }
}
