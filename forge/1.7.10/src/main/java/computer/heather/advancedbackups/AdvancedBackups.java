package computer.heather.advancedbackups;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ThreadedFileIOBase;

import org.apache.logging.log4j.Logger;

import computer.heather.advancedbackups.client.ABClientContactor;
import computer.heather.advancedbackups.client.ABClientRenderer;
import computer.heather.advancedbackups.core.ABCore;
import computer.heather.advancedbackups.core.backups.BackupWrapper;
import computer.heather.advancedbackups.core.backups.BackupTimer;
import computer.heather.advancedbackups.core.config.ConfigManager;
import computer.heather.advancedbackups.network.NetworkHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.Consumer;

@Mod(modid = AdvancedBackups.MODID, name = AdvancedBackups.NAME, acceptableRemoteVersions = "*")
public class AdvancedBackups
{
    public static final String MODID = "advancedbackups";
    public static final String NAME = "Advanced Backups";

    private static Logger LOGGER;
    public static Consumer<String> infoLogger;
    public static Consumer<String> warningLogger;
    public static Consumer<String> errorLogger;

    public static ArrayList<String> players = new ArrayList<>();

    public static MinecraftServer server;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        LOGGER = event.getModLog();
        infoLogger = LOGGER::info;
        warningLogger = LOGGER::warn;
        errorLogger =  LOGGER::error;
    }



    public AdvancedBackups()
    {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(ABClientRenderer.INSTANCE);
        FMLCommonHandler.instance().bus().register(ABClientRenderer.INSTANCE);
		NetworkHandler.init();
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event)
    {
        // Do something when the server starts
        ABCore.worldName = event.getServer().worldServers[0].getWorldInfo().getWorldName();

        //Yes, this works. Yes, it feels FUCKING ILLEGAL
        if (event.getSide() == Side.SERVER) {
            ABCore.worldDir = new File(event.getServer().getFolderName(), "./").toPath();
        }
        else {
            ABCore.worldDir = new File("saves/" + event.getServer().getFolderName(), "./").toPath();
        }
        // the extra ./ is because some of the code in core calls a getParent as it was required when devving in my forge 1.18 instance, but versions earlier than 1.16 do not have this requirement

        server = event.getServer();

        ABCore.disableSaving = AdvancedBackups::disableSaving;
        ABCore.enableSaving = AdvancedBackups::enableSaving;
        ABCore.saveOnce = AdvancedBackups::saveOnce;
        ABCore.flushWrites = AdvancedBackups::flushWrites;

        ABCore.infoLogger = infoLogger;
        ABCore.warningLogger = warningLogger;
        ABCore.errorLogger = errorLogger;

        ABCore.clientContactor = new ABClientContactor();
        ABCore.resetActivity = AdvancedBackups::resetActivity;

        event.registerServerCommand(new AdvancedBackupsCommand());

        ABCore.modJar = Loader.instance().getIndexedModList().get("advancedbackups").getSource(); 

        ConfigManager.loadOrCreateConfig();
        LOGGER.info("Config loaded!!");
        
    }


    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        BackupWrapper.checkStartupBackups();
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        BackupWrapper.checkShutdownBackups();
    }

    @SubscribeEvent
    public void onPlayerConnected(PlayerEvent.PlayerLoggedInEvent event) {
        ABCore.setActivity(true);
    }

    @SubscribeEvent
    public void onTickEnd(TickEvent.ServerTickEvent event) {
        if (!event.phase.equals(TickEvent.Phase.END)) return;
        BackupTimer.check();
    }
    
    


    //fun fact : this boolean is named wrong in MCP mappings!
    //reference : net.minecraft.command.server.CommandSaveOff and CommandSaveOn
    //notice how off sets the boolean to true, and on sets it to false!
    public static void disableSaving() {
        MinecraftServer server = AdvancedBackups.server;
        for (WorldServer level : server.worldServers) {
            if (level != null && !level.levelSaving) {
                level.levelSaving = true;
            }
        }
    }

    public static void enableSaving() {
        MinecraftServer server = AdvancedBackups.server;
        for (WorldServer level : server.worldServers) {
            if (level != null && level.levelSaving) {
                level.levelSaving = false;
            }
        }
    }

    public static void saveOnce(boolean unused) { //flush doesn't seem to be an option in 1.7.10
        try {
            MinecraftServer server = AdvancedBackups.server;
            if (server.getConfigurationManager() != null)
            {
                server.getConfigurationManager().saveAllPlayerData();
            }
            
            int i;
            WorldServer worldserver;
            boolean flag;
            
            for (i = 0; i < server.worldServers.length; ++i)
            {
                if (server.worldServers[i] != null)
                {
                    worldserver = server.worldServers[i];
                    flag = worldserver.levelSaving;
                    worldserver.levelSaving = false;
                    worldserver.saveAllChunks(true, (IProgressUpdate)null);
                    worldserver.levelSaving = flag;
                }
            }
        } catch (MinecraftException e) {
            // TODO Scream at user
            errorLogger.accept("FAILED TO SAVE WORLD!");
            e.printStackTrace();
        }
    }

    // saveAllChunks only queues chunk writes, and Hodgepodge queues level.dat & co. on the same IO thread,
    // so without this the backup thread reads files mid-write. Mirrors ServerUtilities' backup preparation.
    public static void flushWrites() {
        try {
            flushChunkSaves();
            drainQueuedWrites();
        } catch (Exception | LinkageError e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            errorLogger.accept("FAILED TO FLUSH QUEUED WORLD WRITES! Backup may miss the latest save.");
            e.printStackTrace();
        }
    }

    private static void flushChunkSaves() throws InterruptedException {
        // Let the worker finish first: concurrent writers can commit older chunk data last.
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            boolean suspended = world.levelSaving;
            try {
                world.levelSaving = false;
                // Vanilla can remove a loader just after new work is submitted to it. Drain that stranded work too.
                world.saveChunkData();
            } finally {
                world.levelSaving = suspended;
            }
        }
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
    }

    private static void drainQueuedWrites() throws Exception {
        ThreadedFileIOBase.threadedIOInstance.waitForFinish();
        if (!Loader.isModLoaded("hodgepodge")) return;
        Class<?> tweaks;
        try {
            tweaks = Class.forName("com.mitchej123.hodgepodge.config.TweaksConfig");
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            if (!tweaks.getField("threadedWorldDataSaving").getBoolean(null)) return;
        } catch (NoSuchFieldException e) {
            return;
        }
        Class<?> saver = Class.forName("com.mitchej123.hodgepodge.util.WorldDataSaver");
        Method flush;
        try {
            flush = saver.getMethod("flush");
        } catch (NoSuchMethodException e) {
            warningLogger.accept("Hodgepodge WorldDataSaver has no flush(); queued writes drained, but write failures cannot be confirmed");
            return;
        }
        try {
            flush.invoke(saver.getField("INSTANCE").get(null));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }


    public static void resetActivity() {
        ServerConfigurationManager configurationManager = server.getConfigurationManager();
        ABCore.setActivity(!configurationManager.playerEntityList.isEmpty());
    }

}
