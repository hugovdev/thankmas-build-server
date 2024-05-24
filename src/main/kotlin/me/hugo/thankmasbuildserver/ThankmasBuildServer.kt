package me.hugo.thankmasbuildserver

import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmasbuildserver.command.PushMapCommand
import revxrsal.commands.bukkit.BukkitCommandHandler


public class ThankmasBuildServer : ThankmasPlugin(listOf("build_server")) {

    private lateinit var commandHandler: BukkitCommandHandler

    public companion object {
        private var instance: ThankmasBuildServer? = null

        public fun instance(): ThankmasBuildServer {
            val instance = instance
            requireNotNull(instance) { "Tried to fetch a ThankmasPlugin instance while it's null!" }

            return instance
        }
    }

    override fun onEnable() {
        super.onEnable()

        instance = this

        commandHandler = BukkitCommandHandler.create(this)

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