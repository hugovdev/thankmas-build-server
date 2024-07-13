package me.hugo.thankmasbuildserver.command

import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.world.s3.S3WorldSynchronizer
import me.hugo.thankmasbuildserver.ThankmasBuildServer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
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
        val scopeDirectory = configProvider.getOrLoad("build_server/scoped_worlds.yml").getString(world)

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

        val scopeDirectory = configProvider.getOrLoad("build_server/scoped_worlds.yml").getString(world)

        if (scopeDirectory == null) {
            sender.sendTranslated("maps.error.not_scoped")
            return
        }

        sender.sendTranslated("maps.saving") {
            parsed("map", world)
            parsed("scope", scopeDirectory)
        }

        // Save the world before the push!
        bukkitWorld.save()

        sender.sendTranslated("maps.pushing") {
            parsed("map", world)
            parsed("scope", scopeDirectory)
        }

        logger.info("Started pushing the map $world to $scopeDirectory.")

        beingPushed += world

        object : BukkitRunnable() {
            override fun run() {
                try {
                    s3WorldSynchronizer.uploadWorld(bukkitWorld, scopeDirectory)

                    object : BukkitRunnable() {
                        override fun run() {
                            sender.sendTranslated("maps.success") {
                                parsed("map", world)
                                parsed("scope", scopeDirectory)
                            }

                            beingPushed -= world
                            logger.info("Push of map $scopeDirectory has succeeded!")
                        }
                    }.runTask(ThankmasBuildServer.instance())
                } catch (exception: S3Exception) {
                    sender.sendTranslated("maps.error.other") {
                        parsed("map", world)
                        parsed("scope", scopeDirectory)
                    }

                    beingPushed -= world
                    exception.printStackTrace()
                }
            }
        }.runTaskAsynchronously(ThankmasBuildServer.instance())
    }

}