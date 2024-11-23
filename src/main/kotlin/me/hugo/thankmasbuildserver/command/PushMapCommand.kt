package me.hugo.thankmasbuildserver.command

import com.infernalsuite.aswm.api.AdvancedSlimePaperAPI
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.world.SlimeWorldRegistry
import me.hugo.thankmas.world.s3.S3WorldSynchronizer
import me.hugo.thankmasbuildserver.ThankmasBuildServer
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.koin.core.component.inject
import revxrsal.commands.annotation.*
import revxrsal.commands.bukkit.annotation.CommandPermission
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Command that allows players to push a map to the GitHub scopes
 * repository for productions servers to download.
 */
@Command("maps")
@CommandPermission("buildserver.maps")
public class PushMapCommand : TranslatedComponent {

    private val beingPushed: MutableSet<String> = mutableSetOf()

    private val slimeWorldRegistry: SlimeWorldRegistry by inject()

    private val configProvider: ConfigurationProvider by inject()
    private val s3WorldSynchronizer: S3WorldSynchronizer by inject()

    private val logger = ThankmasBuildServer.instance().logger

    @DefaultFor("~", "~ help")
    private fun defaultCommand(sender: Player) {
        sender.sendTranslated("maps.help")
    }

    @Subcommand("scope")
    @AutoComplete("@pushableWorlds")
    private fun sendScope(sender: Player, world: String) {
        val scopeDirectory = configProvider.getOrLoad("build_server/scoped_worlds.yml").getString("$world.scope")

        if (scopeDirectory == null) {
            sender.sendTranslated("maps.error.not_scoped")
            return
        }

        sender.sendTranslated("maps.scope") {
            parsed("scope", scopeDirectory)
            parsed("map", world)
        }
    }

    @Subcommand("push")
    @AutoComplete("@pushableWorlds *")
    private fun pushMap(sender: Player, @Optional world: String = sender.world.name) {
        if (world in beingPushed) {
            sender.sendTranslated("maps.error.already_pushing")
            return
        }

        val bukkitWorld = Bukkit.getWorld(world)

        if (bukkitWorld == null) {
            sender.sendTranslated("maps.error.non_existent")
            return
        }

        val scopedWorldsConfig = configProvider.getOrLoad("build_server/scoped_worlds.yml")

        val isSlime = scopedWorldsConfig.getBoolean("$world.slime", false)
        val scopeDirectory = scopedWorldsConfig.getString("$world.scope")

        if (scopeDirectory == null) {
            sender.sendTranslated("maps.error.not_scoped")
            return
        }

        sender.sendTranslated("maps.saving") {
            parsed("map", world)
            parsed("scope", scopeDirectory)
        }

        // Save the world with flush before the push!
        s3WorldSynchronizer.saveWorldWithFlush(bukkitWorld)

        val oldLocations = bukkitWorld.players.associateWith { it.location }

        // World has to be unloaded before importing it!
        if (isSlime) {
            slimeWorldRegistry.slimeWorldContainer.resolve("$world.slime").delete()
            bukkitWorld.players.forEach { it.teleport(Bukkit.getWorld("world")!!.spawnLocation) }
            Bukkit.unloadWorld(world, true)

            sender.sendTranslated("maps.temporary_teleport") {
                parsed("map", world)
                parsed("scope", scopeDirectory)
            }
        }

        sender.sendTranslated("maps.pushing") {
            parsed("map", world)
            parsed("scope", scopeDirectory)
        }

        logger.info("Started pushing the map $world to $scopeDirectory.")

        beingPushed += world

        Bukkit.getScheduler().runTaskAsynchronously(ThankmasBuildServer.instance(), Runnable {
            try {
                if (isSlime) {
                    val slimePaperAPI = AdvancedSlimePaperAPI.instance()

                    // Save SlimeWorld in memory!
                    val slimeWorld = slimePaperAPI.readVanillaWorld(
                        Bukkit.getWorldContainer().resolve(world),
                        world,
                        slimeWorldRegistry.defaultSlimeLoader
                    )

                    // Save into a file!
                    slimePaperAPI.saveWorld(slimeWorld)

                    // Upload the slime file!
                    s3WorldSynchronizer.uploadFile(
                        slimeWorldRegistry.slimeWorldContainer.resolve("$world.slime"),
                        scopeDirectory
                    )
                } else s3WorldSynchronizer.uploadWorld(bukkitWorld, scopeDirectory)

                Bukkit.getScheduler().runTask(ThankmasBuildServer.instance(), Runnable {
                    sender.sendTranslated("maps.success") {
                        parsed("map", world)
                        parsed("scope", scopeDirectory)
                    }

                    if (isSlime) {
                        val newWorld = Bukkit.createWorld(WorldCreator(world))

                        // Teleport players back!
                        oldLocations.forEach { (player, location) ->
                            location.world = newWorld
                            if (player.isOnline) player.teleport(location)
                        }
                    }

                    beingPushed -= world
                    logger.info("Push of map $scopeDirectory has succeeded!")
                })

            } catch (exception: S3Exception) {
                sender.sendTranslated("maps.error.other") {
                    parsed("map", world)
                    parsed("scope", scopeDirectory)
                }

                beingPushed -= world
                exception.printStackTrace()
            }
        })
    }

}