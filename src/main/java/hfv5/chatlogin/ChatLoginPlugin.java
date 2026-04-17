package hfv5.chatlogin;

import fr.xephi.authme.api.v3.AuthMeApi;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatLoginPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private enum AuthState { WAITING_PASSWORD, WAITING_LOGIN }
    private final Map<UUID, AuthState> authStates = new ConcurrentHashMap<>();
    private AuthMeApi authMeApi;
    private boolean isPluginActive = true;
    private static final long JOIN_DELAY_TICKS = 40L;

    @Override
    public void onEnable() {
        this.authMeApi = AuthMeApi.getInstance();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("chatlogin").setExecutor(this);
        getCommand("chatlogin").setTabCompleter(this);
        getLogger().info("ChatLogin v1.4.0 started!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatlogin.admin")) {
            sender.sendMessage(Component.text("У вас нет прав для использования этой команды.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Использование: /chatlogin <enable|disable>", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("enable")) {
            if (isPluginActive) {
                sender.sendMessage(Component.text("ChatLogin уже включен.", NamedTextColor.YELLOW));
            } else {
                isPluginActive = true;
                sender.sendMessage(Component.text("ChatLogin успешно включен!", NamedTextColor.GREEN));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("disable")) {
            if (!isPluginActive) {
                sender.sendMessage(Component.text("ChatLogin уже выключен.", NamedTextColor.YELLOW));
            } else {
                isPluginActive = false;
                authStates.clear();
                sender.sendMessage(Component.text("ChatLogin выключен!", NamedTextColor.RED));
            }
            return true;
        }
        sender.sendMessage(Component.text("Неизвестный аргумент. Доступно: enable, disable", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("chatlogin.admin")) {
            if ("enable".startsWith(args[0].toLowerCase())) completions.add("enable");
            if ("disable".startsWith(args[0].toLowerCase())) completions.add("disable");
        }
        return completions;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isPluginActive) return;
        Player player = event.getPlayer();

        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            if (authMeApi.isAuthenticated(player)) return;

            if (authMeApi.isRegistered(player.getName())) {
                authStates.put(player.getUniqueId(), AuthState.WAITING_LOGIN);
                player.sendMessage(Component.text("Введите пароль в чат:", NamedTextColor.YELLOW));
            } else {
                authStates.put(player.getUniqueId(), AuthState.WAITING_PASSWORD);
                player.sendMessage(Component.text("Придумайте пароль (минимум 8 символов) и введите в чат:", NamedTextColor.GREEN));
            }
        }, JOIN_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPaperChat(AsyncChatEvent event) {
        if (!isPluginActive) return;
        Player player = event.getPlayer();

        if (authMeApi.isAuthenticated(player)) {
            authStates.remove(player.getUniqueId());
            return;
        }

        AuthState state = authStates.get(player.getUniqueId());
        if (state == null) return;

        event.setCancelled(true);
        event.viewers().clear();

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        event.message(Component.empty());

        if (state == AuthState.WAITING_PASSWORD) {
            handleNewPassword(player, input);
        } else {
            handleLogin(player, input);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (!isPluginActive) return;
        Player player = event.getPlayer();
        if (authStates.containsKey(player.getUniqueId()) && !authMeApi.isAuthenticated(player)) {
            event.setCancelled(true);
            event.getRecipients().clear();
            event.setMessage("");
        }
    }

    private void handleNewPassword(Player player, String password) {
        if (password.length() < 8) {
            player.sendMessage(Component.text("Слишком короткий пароль! Минимум 8 символов.", NamedTextColor.RED));
            return;
        }

        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;

            if (authMeApi.isRegistered(player.getName())) {
                authStates.put(player.getUniqueId(), AuthState.WAITING_LOGIN);
                player.sendMessage(Component.text("Вы уже зарегистрированы. Введите свой пароль:", NamedTextColor.YELLOW));
                handleLogin(player, password);
                return;
            }

            authMeApi.registerPlayer(player.getName(), password);
            boolean registered = authMeApi.isRegistered(player.getName());
            
            if (registered) {
                authMeApi.forceLogin(player);
                boolean loggedIn = authMeApi.isAuthenticated(player);
                
                authStates.remove(player.getUniqueId());
                if (loggedIn) {
                    player.sendMessage(Component.text("Успешная регистрация! Добро пожаловать.", NamedTextColor.GREEN));
                } else {
                    authStates.put(player.getUniqueId(), AuthState.WAITING_LOGIN);
                    player.sendMessage(Component.text("Регистрация прошла успешно! Теперь введите пароль для входа:", NamedTextColor.YELLOW));
                }
            } else {
                player.sendMessage(Component.text("Ошибка регистрации. Попробуйте снова.", NamedTextColor.RED));
            }
        });
    }

    private void handleLogin(Player player, String password) {
        getServer().getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            if (authMeApi.isAuthenticated(player)) {
                authStates.remove(player.getUniqueId());
                return;
            }

            if (authMeApi.checkPassword(player.getName(), password)) {
                
                authMeApi.forceLogin(player);
                boolean loggedIn = authMeApi.isAuthenticated(player);
                
                if (loggedIn) {
                    authStates.remove(player.getUniqueId());
                    player.sendMessage(Component.text("С возвращением!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Пожалуйста, подождите...", NamedTextColor.GRAY));
                    getServer().getScheduler().runTaskLater(this, () -> {
                        if (!player.isOnline()) return;
                        if (authMeApi.isAuthenticated(player)) {
                            authStates.remove(player.getUniqueId());
                            return;
                        }
                        
                        authMeApi.forceLogin(player);
                        boolean retryLogin = authMeApi.isAuthenticated(player);
                        
                        if (retryLogin) {
                            authStates.remove(player.getUniqueId());
                            player.sendMessage(Component.text("С возвращением!", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Не удалось войти. Попробуйте ещё раз:", NamedTextColor.RED));
                        }
                    }, 20L);
                }
            } else {
                player.sendMessage(Component.text("Неверный пароль!", NamedTextColor.RED));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authStates.remove(event.getPlayer().getUniqueId());
    }
}