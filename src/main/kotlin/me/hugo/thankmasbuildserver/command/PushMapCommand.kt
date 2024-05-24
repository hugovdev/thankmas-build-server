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
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*

/**
 * Command that allows players to push a map to the GitHub scopes
 * repository for productions servers to download.
 */
public class PushMapCommand : TranslatedComponent {

    private val configProvider: ConfigurationProvider by inject()
    private val gitHubHelper: GitHubHelper by inject()

    private val logger = ThankmasBuildServer.instance().logger

    @Command("push")
    @CommandPermission("buildserver.push")
    private fun pushMap(
        sender: Player,
        @Optional worldName: String = sender.world.name,
        @Optional commitName: String =
            "[$worldName] World Update - ${SimpleDateFormat("dd/MM/yyyy, HH:mm:ss").format(Date())}"
    ) {
        val world = Bukkit.getWorld(worldName)

        val gitConfig = configProvider.getOrResources("git.yml", "base")

        if (gitConfig.getString("access-token") == null || gitConfig.getString("github-api-url") == null) {
            sender.sendTranslated("map_push.error.no_git")
            return
        }

        if (world == null) {
            sender.sendTranslated("map_push.error.non_existent")
            return
        }

        val scopeDirectory = configProvider.getOrLoad("build_server/scoped_worlds.yml").getString(worldName)

        if (scopeDirectory == null) {
            sender.sendTranslated("map_push.error.not_scoped")
            return
        }

        sender.sendTranslated("map_push.saving") {
            parsed("map", worldName)
            parsed("scope", scopeDirectory)
        }

        // Save the world before the push!
        world.save()

        val worldPath = Bukkit.getWorldContainer().resolve(worldName)

        sender.sendTranslated("map_push.pushing") {
            parsed("map", worldName)
            parsed("scope", scopeDirectory)
        }

        logger.info("Started pushing the map $worldName to $scopeDirectory.")

        object : BukkitRunnable() {
            override fun run() {
                pushWorld(sender.uniqueId, worldPath, scopeDirectory, commitName) {
                    sender.sendTranslated("map_push.success") {
                        parsed("map", worldName)
                        parsed("scope", scopeDirectory)
                    }
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
            localPath.listFiles().forEach { localFile ->
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
                it.sendActionBar(it.translate("map_push.progress") {
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