package dev.ilgax.wynnhidepet.client

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
import net.minecraft.entity.decoration.InteractionEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.passive.WanderingTraderEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.TypeFilter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DebugCommand {

    private const val DEFAULT_RADIUS = 10

    private var ticksRemaining = 0   // >0: collecting, <0: visual-only ring, 0: inactive
    private var searchRadius = DEFAULT_RADIUS.toDouble()
    private val entitySnapshots = LinkedHashMap<Int, String>()
    private var localTick = 0

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("whp")
                    .then(
                        literal("debug")
                            .executes { ctx ->
                                beginInstant(ctx.source, DEFAULT_RADIUS.toDouble())
                                1
                            }
                            .then(
                                argument("seconds", IntegerArgumentType.integer(0, 300))
                                    .executes { ctx ->
                                        val s = IntegerArgumentType.getInteger(ctx, "seconds")
                                        if (s == 0) beginInstant(ctx.source, DEFAULT_RADIUS.toDouble())
                                        else beginTimed(ctx.source, s, DEFAULT_RADIUS.toDouble())
                                        1
                                    }
                                    .then(
                                        argument("range", IntegerArgumentType.integer(1, 512))
                                            .executes { ctx ->
                                                val s = IntegerArgumentType.getInteger(ctx, "seconds")
                                                val r = IntegerArgumentType.getInteger(ctx, "range").toDouble()
                                                if (s == 0) beginInstant(ctx.source, r)
                                                else beginTimed(ctx.source, s, r)
                                                1
                                            }
                                    )
                            )
                    )
            )
        }
    }

    fun tick(client: MinecraftClient) {
        localTick++
        spawnRingParticles(client)

        if (ticksRemaining < 0) {
            ticksRemaining++
        } else if (ticksRemaining > 0) {
            ticksRemaining--
            collectSnapshot(client)

            if (ticksRemaining == 0) {
                writeFile(client)
                client.player?.sendMessage(
                    Text.literal("§aDebug session complete. Written to wynnhidepet-debug-*.txt"),
                    false
                )
            }
        }
    }

    private fun beginInstant(source: FabricClientCommandSource, radius: Double) {
        entitySnapshots.clear()
        searchRadius = radius
        ticksRemaining = 0
        collectSnapshot(source.client)
        writeFile(source.client)
        source.sendFeedback(Text.literal("§aSnapshot written to wynnhidepet-debug-*.txt (radius ${radius.toInt()}b)"))
        ticksRemaining = -60 // show ring for exactly 3 seconds
    }

    private fun beginTimed(source: FabricClientCommandSource, seconds: Int, radius: Double) {
        entitySnapshots.clear()
        searchRadius = radius
        ticksRemaining = seconds * 20
        source.sendFeedback(
            Text.literal("§eCollecting debug data for ${seconds}s within §b${radius.toInt()} blocks§e...")
        )
    }

    private fun collectSnapshot(client: MinecraftClient) {
        val world = client.world ?: return
        val player = client.player ?: return
        val searchBox = player.boundingBox.expand(searchRadius)

        world.getOtherEntities(player, searchBox).forEach { entity ->
            if (entity !is PlayerEntity) {
                entitySnapshots[entity.id] = snapshotEntity(entity, player, client)
            }
        }
    }

    private fun spawnRingParticles(client: MinecraftClient) {
        if (ticksRemaining == 0) return
        val player = client.player ?: return
        if (localTick % 5 != 0) return

        val particleType = if (ticksRemaining > 0) ParticleTypes.END_ROD else ParticleTypes.HAPPY_VILLAGER
        val points = 40
        val y = player.y + 0.1

        for (i in 0 until points) {
            val angle = (i.toDouble() / points) * 2 * Math.PI
            val x = player.x + searchRadius * cos(angle)
            val z = player.z + searchRadius * sin(angle)
            client.particleManager.addParticle(particleType, x, y, z, 0.0, 0.05, 0.0)
        }
    }

    private fun snapshotEntity(entity: Entity, player: PlayerEntity, client: MinecraftClient): String {
        val sb = StringBuilder()
        val dx = entity.x - player.x
        val dy = entity.y - player.y
        val dz = entity.z - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        sb.appendLine("╔══════════════════════════════════════════════════════════")
        sb.appendLine("║ Entity ID: ${entity.id}   Distance: ${fmt(dist)} blocks   Age: ${entity.age}t")
        sb.appendLine("╚══════════════════════════════════════════════════════════")

        sb.appendLine("  [BASE]")
        sb.appendLine("    Class (simple):  ${entity.javaClass.simpleName}")
        sb.appendLine("    Registry type:   ${Registries.ENTITY_TYPE.getId(entity.type)}")
        sb.appendLine("    Age (ticks):     ${entity.age}")
        sb.appendLine("    Position:        x=${fmt(entity.x)}, y=${fmt(entity.y)}, z=${fmt(entity.z)}")
        sb.appendLine("    BoundingBox:     ${entity.boundingBox}")
        sb.appendLine("    Width:           ${entity.width}")
        sb.appendLine("    Height:          ${entity.height}")

        sb.appendLine("  [NAMES]")
        sb.appendLine("    Name:            ${entity.name.string}")
        sb.appendLine("    DisplayName:     ${entity.displayName?.string ?: "none"}")

        sb.appendLine("  [HIERARCHY]")
        sb.appendLine("    InPetIds:        ${entity.id in PetEntityTracker.petEntityIds}")
        sb.appendLine("    Vehicle:         ${entity.vehicle?.let { "ID=${it.id} type=${it.javaClass.simpleName}" } ?: "none"}")
        val passengers = entity.passengerList
        if (passengers.isEmpty()) {
            sb.appendLine("    Passengers:      none")
        } else {
            sb.appendLine("    Passengers (${passengers.size}):")
            appendPassengers(sb, passengers, "      ")
        }

        if (entity is TextDisplayEntity) {
            val text = entity.text.string
            sb.appendLine("  [TEXT DISPLAY]")
            sb.appendLine("    Text:            $text")
            sb.appendLine("    HasWynnSign:     ${text.contains("\uE060")}")
        }

        if (entity is InteractionEntity) {
            sb.appendLine("  [INTERACTION]")
            sb.appendLine("    Width:           ${entity.width}")
            sb.appendLine("    Height:          ${entity.height}")
        }

        sb.appendLine()
        return sb.toString()
    }

    private fun appendPassengers(sb: StringBuilder, passengers: List<Entity>, indent: String) {
        for (p in passengers) {
            sb.appendLine("${indent}ID=${p.id} class=${p.javaClass.simpleName} type=${Registries.ENTITY_TYPE.getId(p.type)} inPetIds=${p.id in PetEntityTracker.petEntityIds}")
            if (p.passengerList.isNotEmpty()) {
                appendPassengers(sb, p.passengerList, "$indent  ")
            }
        }
    }

    private fun buildAnnotations(client: MinecraftClient): Map<Int, String> {
        val world = client.world ?: return emptyMap()
        val player = client.player ?: return emptyMap()
        val annotations = mutableMapOf<Int, String>()
        val searchBox = player.boundingBox.expand(searchRadius)

        val interactions = world.getEntitiesByType(
            TypeFilter.instanceOf(InteractionEntity::class.java), searchBox) { true }

        for (interaction in interactions) {
            if (interaction.width < 0.2f || interaction.width > 1.2f || interaction.height < 0.4f || interaction.height > 2.5f) {
                annotations[interaction.id] = "IGNORED — Dimension mismatch (${fmt(interaction.width.toDouble())}x${fmt(interaction.height.toDouble())})"
                continue
            }

            val interactionAge = interaction.age
            val clusterBox = interaction.boundingBox.expand(2.0, 5.0, 2.0)

            val nearby = world.getOtherEntities(interaction, clusterBox) { 
                it.id != interaction.id && 
                it !is PlayerEntity && 
                it !is InteractionEntity &&
                it !is VillagerEntity &&
                it !is WanderingTraderEntity &&
                kotlin.math.abs(it.age - interactionAge) <= 2
            }

            val actualPetParts = nearby.filter {
                it !is TextDisplayEntity || it.text.string.contains("\uE060")
            }

            if (actualPetParts.isEmpty()) {
                annotations[interaction.id] = "MISSED — Valid dimensions but no matching parts found (Age match text requires \\uE060 signature)"
                continue
            }

            annotations[interaction.id] = "DETECTED — Hitbox (age=$interactionAge, parts=${actualPetParts.size})"

            for (entity in actualPetParts) {
                val reason = if (entity is TextDisplayEntity) "Age+Signature match" else "Age match $interactionAge"
                annotations[entity.id] = "DETECTED — Component ($reason, interaction=${interaction.id})"
                annotatePassengersRecursive(entity, annotations, interaction.id)
            }
        }

        return annotations
    }

    private fun annotatePassengersRecursive(entity: Entity, annotations: MutableMap<Int, String>, interactionId: Int) {
        for (passenger in entity.passengerList) {
            annotations[passenger.id] = "DETECTED — Passenger of component ${entity.id} (interaction=$interactionId)"
            annotatePassengersRecursive(passenger, annotations, interactionId)
        }
    }

    private fun writeFile(client: MinecraftClient) {
        val player = client.player ?: return
        val now = LocalDateTime.now()
        val timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val file = File(client.runDirectory, "wynnhidepet-debug-$timestamp.txt")

        val annotations = buildAnnotations(client)

        file.bufferedWriter().use { w ->
            w.appendLine("WynnHidePet Debug Dump")
            w.appendLine("Generated:  ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            w.appendLine("Player:     ${player.name.string}")
            w.appendLine("Position:   x=${fmt(player.x)}, y=${fmt(player.y)}, z=${fmt(player.z)}")
            w.appendLine("Radius:     ${searchRadius.toInt()} blocks")
            w.appendLine("PetIds:     ${PetEntityTracker.petEntityIds.sorted()}")
            w.appendLine("Entities:   ${entitySnapshots.size} captured")
            w.appendLine("=".repeat(60))

            w.newLine()
            w.appendLine("── CLUSTER ANALYSIS ─────────────────────────────────────")
            val detected = annotations.filterValues { it.startsWith("DETECTED") }
            val missed   = annotations.filterValues { it.startsWith("MISSED") }
            w.appendLine("  Detected entity IDs (${detected.size}): ${detected.keys.sorted()}")
            w.appendLine("  Missed   entity IDs (${missed.size}): ${missed.keys.sorted()}")
            w.newLine()
            if (missed.isNotEmpty()) {
                w.appendLine("  Missed details:")
                missed.forEach { (id, reason) -> w.appendLine("    ID $id — $reason") }
                w.newLine()
            }
            w.appendLine("=".repeat(60))
            w.newLine()

            entitySnapshots.forEach { (id, snapshot) ->
                val annotation = annotations[id]
                if (annotation != null) {
                    val insertPoint = snapshot.indexOf('\n', snapshot.indexOf("╚══")) + 1
                    val annotated = snapshot.substring(0, insertPoint) +
                        "  [ANALYSIS]\n    Result:          $annotation\n" +
                        snapshot.substring(insertPoint)
                    w.appendLine(annotated)
                } else {
                    w.appendLine(snapshot)
                }
            }
        }

        entitySnapshots.clear()
    }

    private fun fmt(d: Double) = String.format("%.3f", d)
}
