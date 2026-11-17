package computer.heather.advancedbackups.client;

import computer.heather.advancedbackups.AdvancedBackups;
import computer.heather.advancedbackups.core.backups.BackupStatusInstance;
import computer.heather.advancedbackups.core.backups.ThreadedBackup;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import serverutils.lib.util.text_components.Notification;

//Only loaded when ServerUtilities is present - keeps every serverutils reference out of ABClientContactor.
public class ServerUtilitiesNotifier {

    public static void notify(BackupStatusInstance.State state, int percent, boolean all) {
        String text;
        switch (state) {
            case STARTING: text = "Server backup started"; break;
            case STARTED: text = "Server backup in progress: " + percent + "%"; break;
            case COMPLETE: text = "Server backup done!" + ThreadedBackup.lastStats; break;
            case FAILED: text = "Server backup failed! Check the server log."; break;
            case CANCELLED: text = "Server backup cancelled!"; break;
            default: return;
        }
        //"backup" resolves to ServerUtilities' own BACKUP notification type (action bar by default, per-player setting).
        //Not setError(): that marks it important, which shows it as a centre-screen title.
        Notification notification = Notification.of("backup", new ChatComponentText(text));
        notification.getChatStyle().setColor(state == BackupStatusInstance.State.FAILED ? EnumChatFormatting.RED : EnumChatFormatting.LIGHT_PURPLE);
        for (Object player : AdvancedBackups.server.getConfigurationManager().playerEntityList) {
            if (!(player instanceof EntityPlayerMP)) continue;
            EntityPlayerMP playerMP = (EntityPlayerMP) player;
            //Same audience as the AB toasts, minus the "has the AB client mod" filter.
            if (all || playerMP.canCommandSenderUseCommand(3, "advancedbackups") || !AdvancedBackups.server.isDedicatedServer()) {
                notification.send(playerMP);
            }
        }
    }
}
