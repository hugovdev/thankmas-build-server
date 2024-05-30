package me.hugo.thankmasbuildserver.listener

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerInteractEvent

/** Controls dripleaf triggering. */
public class DripleafControl : Listener {

    @EventHandler
    private fun onPlayerStepOnDripleaf(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return

        if (event.action != Action.PHYSICAL) return

        if (block.type == Material.BIG_DRIPLEAF) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onEntityTriggerLeaf(event: EntityChangeBlockEvent) {
        val block = event.block

        if (block.type == Material.BIG_DRIPLEAF) {
            event.isCancelled = true
        }
    }

}