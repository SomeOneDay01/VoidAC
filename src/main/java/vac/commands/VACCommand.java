package vac.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vac.VAC;
import vac.gui.SettingsGUI;
import vac.killaura.KillAuraAnalyzer;
import vac.models.PlayerData;

import java.util.*;
import java.util.stream.Collectors;

public class VACCommand implements CommandExecutor, TabCompleter {

    private final VAC plugin;

    private static final List<String> MAIN = Arrays.asList("ban","profile","lags","confidence","crash","alerts","spectate","report","check","checkvpn","history","freeze","settings","reload","stats","help","update","version","replay");
    private static final List<String> CONF_ACTIONS = Arrays.asList("set","add","remove","reset","info");
    private static final List<String> LAG_VALS = Arrays.asList("5","15","30","60","120");
    private static final List<String> CONF_VALS = Arrays.asList("10","25","50","75","100");

    public VACCommand(VAC plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "ban": return ban(sender,args);
            case "profile": return profile(sender,args);
            case "lags": return lags(sender,args);
            case "confidence": return confidence(sender,args);
            case "crash": return crash(sender,args);
            case "alerts": return alerts(sender,args);
            case "spectate": return spectate(sender,args);
            case "report": return report(sender,args);
            case "check": return check(sender,args);
            case "checkvpn": return checkvpn(sender,args);
            case "history": return history(sender,args);
            case "freeze": return freeze(sender,args);
            case "cps": return cps(sender,args);
            case "reach": return reach(sender,args);
            case "hits": return hits(sender,args);
            case "settings": return settings(sender,args);
            case "reload": return reload(sender);
            case "stats": return stats(sender);
            case "update": return update(sender);
            case "version": return version(sender);
            case "replay": return replay(sender, args);
            default: sendHelp(sender); return true;
        }
    }

    private boolean ban(CommandSender s, String[] a) {
        if (!s.hasPermission("vac.command.ban")) { s.sendMessage(noPerm()); return true; }
        if (a.length<2) { s.sendMessage(msg("usage").replace("{usage}","/vac ban <игрок>")); return true; }
        Player t = Bukkit.getPlayer(a[1]);
        if (t==null||!t.isOnline()) { s.sendMessage(msg("player_not_online")); return true; }
        plugin.getPunishmentManager().banPlayer(t, s instanceof Player?s.getName():"CONSOLE", true);
        s.sendMessage(msg("banned_animation").replace("{player}",t.getName()));
        return true;
    }

    private boolean profile(CommandSender s, String[] a) {
        if (!s.hasPermission("vac.command.profile")) { s.sendMessage(noPerm()); return true; }
        if (a.length<2) { s.sendMessage(msg("usage").replace("{usage}","/vac profile <игрок>")); return true; }
        Player t = Bukkit.getPlayer(a[1]);
        if (t==null||!t.isOnline()) { s.sendMessage(msg("player_not_online")); return true; }
        PlayerData d = plugin.getPlayerDataManager().getOrCreate(t);
        s.sendMessage(msg("profile_header"));
        s.sendMessage(msg("profile_confidence").replace("{confidence}",fmt(d.getConfidence())));
        s.sendMessage(msg("profile_health").replace("{health}",String.valueOf((int)t.getHealth())).replace("{max_health}",String.valueOf((int)t.getMaxHealth())));
        s.sendMessage(msg("profile_food").replace("{food}",String.valueOf(t.getFoodLevel())));
        s.sendMessage(msg("profile_ping").replace("{ping}",String.valueOf(t.spigot().getPing())));
        s.sendMessage(msg("profile_location").replace("{world}",t.getWorld().getName()).replace("{x}",String.valueOf(t.getLocation().getBlockX())).replace("{y}",String.valueOf(t.getLocation().getBlockY())).replace("{z}",String.valueOf(t.getLocation().getBlockZ())));
        s.sendMessage(msg("profile_checks").replace("{total_violations}",String.valueOf(d.getTotalViolations())));
        if (d.getViolations().isEmpty()) s.sendMessage(msg("profile_no_checks"));
        else for (Map.Entry<String,Integer> e : d.getViolations().entrySet())
            s.sendMessage(msg("profile_check_line").replace("{check}",e.getKey()).replace("{violations}",String.valueOf(e.getValue())).replace("{check_confidence}",fmt(e.getValue()*plugin.getConfigManager().getConfidenceIncrement())));
        s.sendMessage(msg("profile_grim_version").replace("{version}",plugin.getGrimACListener()!=null&&plugin.getGrimACListener().isGrimacEnabled()?"§aGrimAC активен":"§cGrimAC не найден"));
        s.sendMessage(msg("profile_footer"));
        return true;
    }

    private boolean lags(CommandSender s, String[] a) {
        if (!s.hasPermission("vac.command.lags")) { s.sendMessage(noPerm()); return true; }
        if (a.length<2) { s.sendMessage(msg("lags_category_usage")); return true; }
        Player t = Bukkit.getPlayer(a[1]);
        if (t==null||!t.isOnline()) { s.sendMessage(msg("player_not_online")); return true; }
        if (a.length>=3&&a[2].equalsIgnoreCase("stop")) {
            if (!plugin.getLagManager().hasActiveLag(t)) { s.sendMessage(msg("lags_not_active").replace("{player}",t.getName())); return true; }
            plugin.getLagManager().stopLag(t); s.sendMessage(msg("lags_stopped").replace("{player}",t.getName())); return true;
        }
        if (a.length<3) { String c=String.join("§7, §c",plugin.getLagManager().getAvailableCategories()); s.sendMessage(msg("lags_invalid_category").replace("{categories}","§c"+c)); return true; }
        String cat=a[2].toLowerCase();
        if (!plugin.getLagManager().getAvailableCategories().contains(cat)) { s.sendMessage(msg("lags_invalid_category").replace("{categories}","§c"+cat)); return true; }
        if (!plugin.getConfigManager().isLagCategoryEnabled(cat)) { s.sendMessage(msg("lags_invalid_category").replace("{categories}","§c"+cat+" §7(откл)")); return true; }
        if (plugin.getLagManager().hasActiveLag(t)) { s.sendMessage(msg("lags_already").replace("{player}",t.getName())); return true; }
        int dur=plugin.getConfigManager().getDefaultLagDuration();
        if (a.length>=4) try{dur=Integer.parseInt(a[3]);}catch(Exception ignored){}
        plugin.getLagManager().startLag(t,cat,dur);
        s.sendMessage(msg("lags_started").replace("{player}",t.getName()).replace("{category}",cat).replace("{duration}",String.valueOf(dur)));
        return true;
    }

    private boolean confidence(CommandSender s, String[] a) {
        if (!s.hasPermission("vac.command.confidence")) { s.sendMessage(noPerm()); return true; }
        if (a.length<2) { s.sendMessage(msg("usage").replace("{usage}","/vac confidence <игрок> <set|add|remove|reset|info> [знач]")); return true; }
        Player t=Bukkit.getPlayer(a[1]); if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        PlayerData d=plugin.getPlayerDataManager().getOrCreate(t);
        if(a.length<3){s.sendMessage(msg("confidence_info").replace("{player}",t.getName()).replace("{confidence}",fmt(d.getConfidence())));return true;}
        switch(a[2].toLowerCase()){
            case"info":s.sendMessage(msg("confidence_info").replace("{player}",t.getName()).replace("{confidence}",fmt(d.getConfidence())));break;
            case"set":if(a.length<4){s.sendMessage(msg("usage").replace("{usage}","/vac confidence <игрок> set <знач>"));return true;}try{d.setConfidence(Double.parseDouble(a[3]));s.sendMessage(msg("confidence_set").replace("{player}",t.getName()).replace("{confidence}",fmt(d.getConfidence())));}catch(Exception e){s.sendMessage("§cНеверное число.");}break;
            case"add":if(a.length<4){s.sendMessage(msg("usage").replace("{usage}","/vac confidence <игрок> add <знач>"));return true;}try{d.addConfidence(Double.parseDouble(a[3]));s.sendMessage(msg("confidence_add").replace("{player}",t.getName()).replace("{amount}",a[3]).replace("{confidence}",fmt(d.getConfidence())));}catch(Exception e){s.sendMessage("§cНеверное число.");}break;
            case"remove":if(a.length<4){s.sendMessage(msg("usage").replace("{usage}","/vac confidence <игрок> remove <знач>"));return true;}try{d.removeConfidence(Double.parseDouble(a[3]));s.sendMessage(msg("confidence_remove").replace("{player}",t.getName()).replace("{amount}",a[3]).replace("{confidence}",fmt(d.getConfidence())));}catch(Exception e){s.sendMessage("§cНеверное число.");}break;
            case"reset":d.setConfidence(0);s.sendMessage(msg("confidence_reset").replace("{player}",t.getName()));break;
            default:s.sendMessage(msg("usage").replace("{usage}","/vac confidence <игрок> <set|add|remove|reset|info> [знач]"));
        }
        return true;
    }

    private boolean crash(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.crash")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac crash <игрок> [метод]"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        String m="book";if(a.length>=3){m=a[2].toLowerCase();if(!plugin.getCrashManager().getAvailableMethods().contains(m)){s.sendMessage(msg("crash_methods").replace("{methods}",String.join("§7,§c",plugin.getCrashManager().getAvailableMethods())));return true;}}
        plugin.getCrashManager().crashPlayer(t,m);s.sendMessage(msg("crash_started").replace("{player}",t.getName()).replace("{method}",m));
        return true;
    }

    private boolean alerts(CommandSender s, String[] a) {
        if(!(s instanceof Player)){s.sendMessage(msg("only_players"));return true;}
        if(!s.hasPermission("vac.alerts")){s.sendMessage(noPerm());return true;}
        Player p=(Player)s;
        if(a.length>=2){
            if(a[1].equalsIgnoreCase("on")){plugin.getAlertManager().setAlertsEnabled(p,true);s.sendMessage(msg("alerts_enabled"));return true;}
            if(a[1].equalsIgnoreCase("off")){plugin.getAlertManager().setAlertsEnabled(p,false);s.sendMessage(msg("alerts_disabled"));return true;}
        }
        plugin.getAlertManager().toggle(p);
        s.sendMessage(plugin.getAlertManager().hasAlertsEnabled(p)?msg("alerts_enabled"):msg("alerts_disabled"));
        return true;
    }

    private boolean spectate(CommandSender s, String[] a) {
        if(!(s instanceof Player)){s.sendMessage(msg("only_players"));return true;}
        if(!s.hasPermission("vac.command.spectate")){s.sendMessage(noPerm());return true;}
        Player p=(Player)s;
        if(a.length<2){
            if(plugin.getSpectateManager().isSpectating(p)){plugin.getSpectateManager().stopSpectate(p);s.sendMessage(msg("spectate_stopped"));return true;}
            s.sendMessage(msg("usage").replace("{usage}","/vac spectate <игрок>"));return true;
        }
        if(a[1].equalsIgnoreCase("stop")){plugin.getSpectateManager().stopSpectate(p);s.sendMessage(msg("spectate_stopped"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        if(t.equals(p)){s.sendMessage("§cНельзя следить за собой.");return true;}
        plugin.getSpectateManager().startSpectate(p,t);
        s.sendMessage(msg("spectate_started").replace("{player}",t.getName()));
        return true;
    }

    private boolean report(CommandSender s, String[] a) {
        if(!(s instanceof Player)){s.sendMessage(msg("only_players"));return true;}
        if(!s.hasPermission("vac.report")){s.sendMessage(noPerm());return true;}
        if(a.length<3){s.sendMessage(msg("usage").replace("{usage}","/vac report <игрок> <причина>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        String reason=String.join(" ",Arrays.copyOfRange(a,2,a.length));
        plugin.getReportManager().report((Player)s,t,reason);
        return true;
    }

    private boolean check(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.check")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac check <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        plugin.getCheckManager().runCheck(t);
        return true;
    }

    private boolean checkvpn(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.admin")){s.sendMessage(noPerm());return true;}
        if(!(s instanceof Player)){s.sendMessage(msg("only_players"));return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac checkvpn <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        plugin.getCheckManager().checkVPN(t,(Player)s);
        return true;
    }

    private boolean history(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.history")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac history <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        plugin.getEvidenceManager().sendHistory((Player)s,t.getUniqueId());
        return true;
    }

    private boolean freeze(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.freeze")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac freeze <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        if(a.length>=3&&a[2].equalsIgnoreCase("off")){plugin.getFreezeManager().unfreeze(t);s.sendMessage(msg("freeze_off"));return true;}
        if(plugin.getFreezeManager().isFrozen(t)){plugin.getFreezeManager().unfreeze(t);s.sendMessage(msg("freeze_off"));return true;}
        plugin.getFreezeManager().freeze(t);
        s.sendMessage(msg("freeze_on"));
        return true;
    }

    private boolean update(CommandSender s) {
        if (!s.hasPermission("vac.command.update")) { s.sendMessage(noPerm()); return true; }
        s.sendMessage(msg("update_checking"));
        if (plugin.getUpdateChecker() != null) {
            plugin.getUpdateChecker().checkAsync();
            s.sendMessage(msg("update_checked"));
        } else {
            s.sendMessage(msg("update_error"));
        }
        return true;
    }

    private boolean version(CommandSender s) {
        if (!s.hasPermission("vac.command.version")) { s.sendMessage(noPerm()); return true; }
        String ver = plugin.getDescription().getVersion();
        s.sendMessage(msg("version_info").replace("{version}", ver));
        return true;
    }

    private boolean replay(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage(msg("only_players")); return true; }
        if (!s.hasPermission("vac.command.replay")) { s.sendMessage(noPerm()); return true; }
        Player p = (Player) s;
        if (a.length < 2) { s.sendMessage(msg("usage").replace("{usage}", "/vac replay <игрок> [save|stop]")); return true; }
        Player t = Bukkit.getPlayer(a[1]);
        if (t == null || !t.isOnline()) { s.sendMessage(msg("player_not_online")); return true; }
        if (a.length >= 3 && a[2].equalsIgnoreCase("save")) {
            plugin.getReplayRecorder().saveReplay(t);
            s.sendMessage(msg("replay_saved").replace("{player}", t.getName()));
            return true;
        }
        plugin.getReplayRecorder().playReplay(p, t);
        return true;
    }

    private boolean settings(CommandSender s, String[] a) {
        if(!(s instanceof Player)){s.sendMessage(msg("only_players"));return true;}
        if(!s.hasPermission("vac.admin")){s.sendMessage(noPerm());return true;}
        SettingsGUI.open((Player)s,plugin);
        return true;
    }

    private boolean reload(CommandSender s) {
        if(!s.hasPermission("vac.command.reload")){s.sendMessage(noPerm());return true;}
        try{plugin.reloadConfigFromFile();plugin.getConfigManager().reload();s.sendMessage(msg("reload_success"));}catch(Exception e){s.sendMessage(msg("reload_fail"));}
        return true;
    }

    private boolean stats(CommandSender s) {
        if(!s.hasPermission("vac.admin")){s.sendMessage(noPerm());return true;}
        s.sendMessage(msg("stats_header"));
        s.sendMessage(msg("stats_players_tracked").replace("{count}",String.valueOf(plugin.getPlayerDataManager().getTrackedPlayerCount())));
        s.sendMessage(msg("stats_checks_total").replace("{count}",String.valueOf(plugin.getPlayerDataManager().getTotalViolations())));
        s.sendMessage(msg("stats_banned").replace("{count}",String.valueOf(plugin.getPlayerDataManager().getTotalBans())));
        s.sendMessage(msg("stats_reports").replace("{count}",String.valueOf(plugin.getReportManager().getAllReports().size())));
        return true;
    }

    private boolean cps(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.cps")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac cps <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        KillAuraAnalyzer.HitStats stats=plugin.getKillAuraAnalyzer().getStats(t);
        s.sendMessage(msg("ka_header").replace("{player}",t.getName()));
        s.sendMessage(msg("ka_cps").replace("{cps}",String.format("%.1f",stats.cps)).replace("{swings}",String.valueOf(stats.swings2s)));
        return true;
    }

    private boolean reach(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.reach")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac reach <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        KillAuraAnalyzer.HitStats stats=plugin.getKillAuraAnalyzer().getStats(t);
        s.sendMessage(msg("ka_header").replace("{player}",t.getName()));
        s.sendMessage(msg("ka_reach").replace("{reach}",String.format("%.2f",stats.avgReach)).replace("{hits}",String.valueOf(stats.totalHits)));
        return true;
    }

    private boolean hits(CommandSender s, String[] a) {
        if(!s.hasPermission("vac.command.hits")){s.sendMessage(noPerm());return true;}
        if(a.length<2){s.sendMessage(msg("usage").replace("{usage}","/vac hits <игрок>"));return true;}
        Player t=Bukkit.getPlayer(a[1]);if(t==null||!t.isOnline()){s.sendMessage(msg("player_not_online"));return true;}
        KillAuraAnalyzer.HitStats stats=plugin.getKillAuraAnalyzer().getStats(t);
        s.sendMessage(msg("ka_header").replace("{player}",t.getName()));
        s.sendMessage(msg("ka_cps").replace("{cps}",String.format("%.1f",stats.cps)).replace("{swings}",String.valueOf(stats.swings2s)));
        s.sendMessage(msg("ka_reach").replace("{reach}",String.format("%.2f",stats.avgReach)).replace("{hits}",String.valueOf(stats.totalHits)));
        s.sendMessage(msg("ka_aim").replace("{aim}",String.format("%.4f",stats.avgAimDev)));
        s.sendMessage(msg("ka_wall").replace("{walls}",String.valueOf(stats.wallHits)).replace("{hits}",String.valueOf(stats.totalHits)));
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(msg("help_header"));
        s.sendMessage(msg("help_ban")); s.sendMessage(msg("help_profile")); s.sendMessage(msg("help_lags"));
        s.sendMessage(msg("help_confidence")); s.sendMessage(msg("help_crash")); s.sendMessage(msg("help_alerts"));
        s.sendMessage(msg("help_spectate")); s.sendMessage(msg("help_report")); s.sendMessage(msg("help_check"));
        s.sendMessage(msg("help_checkvpn")); s.sendMessage(msg("help_history")); s.sendMessage(msg("help_freeze"));
        s.sendMessage(msg("help_cps")); s.sendMessage(msg("help_reach")); s.sendMessage(msg("help_hits"));
        s.sendMessage(msg("help_settings")); s.sendMessage(msg("help_reload"));
        s.sendMessage(msg("help_update")); s.sendMessage(msg("help_version")); s.sendMessage(msg("help_replay"));
        s.sendMessage(msg("help_footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if(!s.hasPermission("vac.admin"))return Collections.emptyList();
        String cur=a[a.length-1].toLowerCase();
        if(a.length==1)return filter(MAIN,cur);
        if(a.length==2){
            String sub=a[0].toLowerCase();
            if(isOnlineCmd(sub))return filter(getPlayers(),cur);
        }
        if(a.length==3){
            String sub=a[0].toLowerCase();
            if(sub.equals("lags")){List<String>c=new ArrayList<>(plugin.getLagManager().getAvailableCategories());c.add("stop");return filter(c,cur);}
            if(sub.equals("confidence"))return filter(CONF_ACTIONS,cur);
            if(sub.equals("crash"))return filter(plugin.getCrashManager().getAvailableMethods(),cur);
            if(sub.equals("alerts")||sub.equals("spectate"))return filter(Arrays.asList("on","off","stop"),cur);
            if(sub.equals("freeze"))return filter(Arrays.asList("off"),cur);
        }
        if(a.length==4){
            String sub=a[0].toLowerCase();
            if(sub.equals("lags"))return filter(LAG_VALS,cur);
            if(sub.equals("confidence")&&(a[2].equalsIgnoreCase("set")||a[2].equalsIgnoreCase("add")||a[2].equalsIgnoreCase("remove")))return filter(CONF_VALS,cur);
        }
        return Collections.emptyList();
    }

    private boolean isOnlineCmd(String sub){return sub.equals("ban")||sub.equals("profile")||sub.equals("lags")||sub.equals("confidence")||sub.equals("crash")||sub.equals("spectate")||sub.equals("report")||sub.equals("check")||sub.equals("checkvpn")||sub.equals("history")||sub.equals("freeze")||sub.equals("cps")||sub.equals("reach")||sub.equals("hits")||sub.equals("replay");}
    private List<String> getPlayers(){return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());}
    private List<String> filter(List<String>l,String p){return l.stream().filter(s->s.toLowerCase().startsWith(p)).collect(Collectors.toList());}
    private String noPerm(){return plugin.getConfigManager().getMessage("no_permission");}
    private String msg(String k){return plugin.getConfigManager().getMessageRaw(k);}
    private String fmt(double d){return String.format("%.1f",d);}
}
