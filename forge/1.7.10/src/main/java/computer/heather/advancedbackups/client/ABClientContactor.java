package computer.heather.advancedbackups.client;


import computer.heather.advancedbackups.AdvancedBackups;
import computer.heather.advancedbackups.core.backups.BackupStatusInstance;
import computer.heather.advancedbackups.interfaces.IClientContactor;
import computer.heather.advancedbackups.network.NetworkHandler;
import computer.heather.advancedbackups.network.PacketBackupStatus;
import cpw.mods.fml.common.Loader;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ServerConfigurationManager;

@SuppressWarnings("unchecked")
public class ABClientContactor implements IClientContactor {

    private long lastAge = -1;
    private int lastPercent = -1;
    private long lastSent = 0;

    @Override
    public void handle(BackupStatusInstance instance, boolean all) {
        if (!Loader.isModLoaded("serverutilities")) {
            IClientContactor.super.handle(instance, all);
            return;
        }
        //BackupTimer re-sends the current instance every tick, so notify once per published instance...
        long now = System.currentTimeMillis();
        int percent = -1;
        if (instance.getState() == BackupStatusInstance.State.STARTED) {
            percent = (int) ((((float) instance.getProgress()) / ((float) instance.getMax())) * 100F);
            //...and progress at most every 3s (ServerUtilities' notification lifetime), so chat-mode players are not flooded.
            if (percent == this.lastPercent || now - this.lastSent < 3000) return;
        } else if (instance.getAge() == this.lastAge) {
            return;
        }
        this.lastAge = instance.getAge();
        this.lastPercent = percent;
        this.lastSent = now;
        ServerUtilitiesNotifier.notify(instance.getState(), percent, all);
    }

    @Override
    public void backupComplete(boolean all) {
        ServerConfigurationManager configurationManager = AdvancedBackups.server.getConfigurationManager();
        PacketBackupStatus packet = new PacketBackupStatus(false, false, false, true, false, 0, 0);
        configurationManager.playerEntityList.forEach((player) -> {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP) player;
                if (!AdvancedBackups.players.contains(playerMP.getGameProfile().getId().toString())) return;
                //if they can run the command, or are in singleplayer, or config says all players, they should receive info on active backups.
                if (playerMP.canCommandSenderUseCommand(3, "advancedbackups") || !AdvancedBackups.server.isDedicatedServer() || all) {
                    NetworkHandler.HANDLER.sendTo(packet, playerMP);
                }
            }
        });
    }

    @Override
    public void backupFailed(boolean all) {
        ServerConfigurationManager configurationManager = AdvancedBackups.server.getConfigurationManager();
        PacketBackupStatus packet = new PacketBackupStatus(false, false, true, false, false, 0, 0);
        configurationManager.playerEntityList.forEach((player) -> {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP) player;
                if (!AdvancedBackups.players.contains(playerMP.getGameProfile().getId().toString())) return;
                //if they can run the command, or are in singleplayer, or config says all players, they should receive info on active backups.
                if (playerMP.canCommandSenderUseCommand(3, "advancedbackups") || !AdvancedBackups.server.isDedicatedServer() || all) {
                    NetworkHandler.HANDLER.sendTo(packet, playerMP);
                }
            }
        });
    }

    @Override
    public void backupProgress(int progress, int max, boolean all) {
        ServerConfigurationManager configurationManager = AdvancedBackups.server.getConfigurationManager();
        PacketBackupStatus packet = new PacketBackupStatus(false, true, false, false, false, progress, max);
        configurationManager.playerEntityList.forEach((player) -> {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP) player;
                if (!AdvancedBackups.players.contains(playerMP.getGameProfile().getId().toString())) return;
                //if they can run the command, or are in singleplayer, or config says all players, they should receive info on active backups.
                if (playerMP.canCommandSenderUseCommand(3, "advancedbackups") || !AdvancedBackups.server.isDedicatedServer() || all) {
                    NetworkHandler.HANDLER.sendTo(packet, playerMP);
                }
            }
        });
    }

    @Override
    public void backupStarting(boolean all) {
        ServerConfigurationManager configurationManager = AdvancedBackups.server.getConfigurationManager();
        PacketBackupStatus packet = new PacketBackupStatus(true, false, false, false, false, 0, 0);
        configurationManager.playerEntityList.forEach((player) -> {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP) player;
                if (!AdvancedBackups.players.contains(playerMP.getGameProfile().getId().toString())) return;
                //if they can run the command, or are in singleplayer, or config says all players, they should receive info on active backups.
                if (playerMP.canCommandSenderUseCommand(3, "advancedbackups") || !AdvancedBackups.server.isDedicatedServer() || all) {
                    NetworkHandler.HANDLER.sendTo(packet, playerMP);
                }
            }
        });
    }

    @Override
    public void backupCancelled(boolean all) {
        ServerConfigurationManager configurationManager = AdvancedBackups.server.getConfigurationManager();
        PacketBackupStatus packet = new PacketBackupStatus(false, false, false, false, true, 0, 0);
        configurationManager.playerEntityList.forEach((player) -> {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP playerMP = (EntityPlayerMP) player;
                if (!AdvancedBackups.players.contains(playerMP.getGameProfile().getId().toString())) return;
                //if they can run the command, or are in singleplayer, or config says all players, they should receive info on active backups.
                if (playerMP.canCommandSenderUseCommand(3, "advancedbackups") || !AdvancedBackups.server.isDedicatedServer() || all) {
                    NetworkHandler.HANDLER.sendTo(packet, playerMP);
                }
            }
        });
    }
    
}
