package me.hugo.thankmasbuildserver.command

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.git.GitHubHelper
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.player
import me.hugo.thankmas.player.translate
import me.hugo.thankmasbuildserver.ThankmasBuildServer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.inject
import revxrsal.commands.annotation.AutoComplete
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.DefaultFor
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*

/**
 * Command that allows players to push a map to the GitHub scopes
 * repository for productions servers to download.
 */
@Command("maps")
@CommandPermission("buildserver.maps")
public class PushMapCommand : TranslatedComponent {

    private val beingPushed: MutableSet<String> = mutableSetOf()

    private val configProvider: ConfigurationProvider by inject()
    private val gitHubHelper: GitHubHelper by inject()

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
    private fun pushMap(
        sender: Player,
        @Optional world: String = sender.world.name,
        @Optional commitMessage: String =
            "[$world] World Update - ${SimpleDateFormat("dd/MM/yyyy, HH:mm:ss").format(Date())}"
    ) {

        if (world in beingPushed) {
            sender.sendTranslated("maps.error.already_pushing")
            return
        }

        val gitConfig = configProvider.getOrResources("git.yml", "base")

        if (gitConfig.getString("access-token") == null || gitConfig.getString("github-api-url") == null) {
            sender.sendTranslated("maps.error.no_git")
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

        val worldPath = Bukkit.getWorldContainer().resolve(world)

        sender.sendTranslated("maps.pushing") {
            parsed("map", world)
            parsed("scope", scopeDirectory)
        }

        logger.info("Started pushing the map $world to $scopeDirectory.")

        beingPushed.add(world)

        object : BukkitRunnable() {
            override fun run() {
                pushWorld(sender.uniqueId, worldPath, scopeDirectory, commitMessage) {
                    sender.sendTranslated("maps.success") {
                        parsed("map", world)
                        parsed("scope", scopeDirectory)
                    }

                    beingPushed.remove(world)
                }
            }
        }.runTaskAsynchronously(ThankmasBuildServer.instance())
    }

    private fun pushWorld(
        sender: UUID,
        originalPath: File,
        scopeDirectory: String,
        commitName: String,
        onSuccess: () -> Unit
    ) {
        // data/raids -> CHANGED
        // region/mc.whatever -> DELETED
        val changes: MutableList<FileChange> = mutableListOf()

        fun checkDirectory(localPath: File, remoteFileList: JsonArray, scopeDirectory: String) {
            localPath.listFiles()?.forEach { localFile ->
                val currentFileName = localFile.name

                val remoteMatch = remoteFileList.firstOrNull { file ->
                    (file as JsonObject).get("name").asString == currentFileName
                }?.asJsonObject

                // If this file is a directory we explore it and match it with the remote
                // directory (if it exists).
                if (localFile.isDirectory) {
                    checkDirectory(
                        localFile,
                        if (remoteMatch?.get("type")?.asString == "dir")
                            gitHubHelper.fetchScope("$scopeDirectory/$currentFileName")
                        else JsonArray(),
                        "$scopeDirectory/$currentFileName"
                    )

                    return@forEach
                }

                // If the file is not a directory we update it or create it.
                changes += FileChange(
                    "${originalPath.toPath().relativize(localPath.toPath()).resolve(currentFileName)}",
                    FileAction.CREATE_OR_CHANGE,
                    remoteMatch?.get("sha")?.asString,
                    Base64.getEncoder().encodeToString(Files.readAllBytes(localFile.toPath()))
                )
            }

            // Files on remote but not on local get removed.
            remoteFileList.map { it.asJsonObject }
                .filter { !localPath.resolve(it.get("name").asString).exists() }.forEach {
                    changes += FileChange(
                        "${originalPath.toPath().relativize(localPath.toPath()).resolve(it.get("name").asString)}",
                        FileAction.DELETE,
                        it.get("sha").asString
                    )
                }
        }

        checkDirectory(originalPath, gitHubHelper.fetchScope(scopeDirectory).asJsonArray, scopeDirectory)

        changes.forEachIndexed { index, fileChange ->
            if (fileChange.action == FileAction.DELETE) gitHubHelper.deleteFile(
                "$scopeDirectory/${fileChange.relativePath}",
                "$commitName - DELETE ${fileChange.relativePath}",
                fileChange.sha!!
            ) else {
                gitHubHelper.pushFileChange(
                    "$scopeDirectory/${fileChange.relativePath}",
                    fileChange.newValue!!,
                    "$commitName - CHANGE/CREATE ${fileChange.relativePath}",
                    fileChange.sha
                )
            }

            sender.player()?.let {
                it.sendActionBar(it.translate("maps.progress") {
                    parsed("index", index + 1)
                    parsed("changes", changes.size)
                    parsed("file", fileChange.relativePath)
                })
            }
        }

        object : BukkitRunnable() {
            override fun run() {
                onSuccess()
                logger.info("Push of map $scopeDirectory has succeeded!")
            }
        }.runTask(ThankmasBuildServer.instance())
    }

    private enum class FileAction {
        CREATE_OR_CHANGE, DELETE;
    }

    private data class FileChange(
        val relativePath: String,
        val action: FileAction,

        // Required when updating a file:
        val sha: String? = null,
        val newValue: String? = null
    ) {
        init {
            ThankmasBuildServer.instance().logger.info("[$action] to file $relativePath")
        }
    }

}