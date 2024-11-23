package me.hugo.thankmasbuildserver

import me.hugo.thankmas.SimpleThankmasPlugin
import me.hugo.thankmasbuildserver.command.PushMapCommand
import me.hugo.thankmasbuildserver.listener.DripleafControl
import org.bukkit.Bukkit
import revxrsal.commands.bukkit.BukkitCommandHandler
import revxrsal.commands.ktx.SuspendFunctionsSupport

public class ThankmasBuildServer : SimpleThankmasPlugin(listOf("build_server")) {

    private lateinit var commandHandler: BukkitCommandHandler

    public companion object {
        private var instance: ThankmasBuildServer? = null

        public fun instance(): ThankmasBuildServer {
            return requireNotNull(instance)
            { "Tried to fetch a ThankmasPlugin instance while it's null!" }
        }
    }

    override fun onEnable() {
        super.onEnable()

        instance = this

        Bukkit.getPluginManager().registerEvents(DripleafControl(), this)

        commandHandler = BukkitCommandHandler.create(this)
        commandHandler.accept(SuspendFunctionsSupport)

        commandHandler.autoCompleter.registerSuggestion("pushableWorlds") { _, _, _ ->
            configProvider.getOrLoad("build_server/scoped_worlds.yml").getKeys(false)
        }

        commandHandler.register(PushMapCommand())
        commandHandler.registerBrigadier()
    }

    override fun onDisable() {
        super.onDisable()

        commandHandler.unregisterAllCommands()
    }

}