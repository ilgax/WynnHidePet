package dev.ilgax.wynnhidepet.client

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display.TextDisplay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.player.Player
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

    fun tick(client: Minecraft) {
        if (ticksRemaining == 0) return
        localTick++
        spawnRingParticles(client)

        if (ticksRemaining < 0) {
            ticksRemaining++
        } else if (ticksRemaining > 0) {
            ticksRemaining--
            collectSnapshot(client)

            if (ticksRemaining == 0) {
                writeFile(client)
                client.player?.displayClientMessage(
                    Component.literal("§aDebug session complete. Written to wynnhidepet-debug-*.txt"),
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
        source.sendFeedback(Component.literal("§aSnapshot written to wynnhidepet-debug-*.txt (radius ${radius.toInt()}b)"))
        ticksRemaining = -60 // show ring for exactly 3 seconds
    }

    private fun beginTimed(source: FabricClientCommandSource, seconds: Int, radius: Double) {
        entitySnapshots.clear()
        searchRadius = radius
        ticksRemaining = seconds * 20
        source.sendFeedback(
            Component.literal("§eCollecting debug data for ${seconds}s within §b${radius.toInt()} blocks§e...")
        )
    }

    private fun collectSnapshot(client: Minecraft) {
        val level = client.level ?: return
        val player = client.player ?: return
        val searchBox = player.boundingBox.inflate(searchRadius)

        level.getEntities(player, searchBox).forEach { entity ->
            if (entity !is Player) {
                entitySnapshots[entity.id] = snapshotEntity(entity, player, client)
            }
        }
    }

    private fun spawnRingParticles(client: Minecraft) {
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
            client.particleEngine.createParticle(particleType, x, y, z, 0.0, 0.05, 0.0)
        }
    }

    private fun snapshotEntity(entity: Entity, player: Player, client: Minecraft): String {
        val sb = StringBuilder()
        val dx = entity.x - player.x
        val dy = entity.y - player.y
        val dz = entity.z - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        sb.appendLine("╔══════════════════════════════════════════════════════════")
        sb.appendLine("║ Entity ID: ${entity.id}   Distance: ${fmt(dist)} blocks   Age: ${entity.tickCount}t")
        sb.appendLine("╚══════════════════════════════════════════════════════════")

        sb.appendLine("  [BASE]")
        sb.appendLine("    Class (simple):  ${entity.javaClass.simpleName}")
        sb.appendLine("    Registry type:   ${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)}")
        sb.appendLine("    Age (ticks):     ${entity.tickCount}")
        sb.appendLine("    Position:        x=${fmt(entity.x)}, y=${fmt(entity.y)}, z=${fmt(entity.z)}")
        sb.appendLine("    BoundingBox:     ${entity.boundingBox}")
        sb.appendLine("    Width:           ${entity.bbWidth}")
        sb.appendLine("    Height:          ${entity.bbHeight}")

        sb.appendLine("  [NAMES]")
        sb.appendLine("    Name:            ${entity.name.string}")
        sb.appendLine("    DisplayName:     ${entity.displayName.string}")

        sb.appendLine("  [HIERARCHY]")
        sb.appendLine("    InPetIds:        ${entity.id in PetEntityTracker.petEntityIds}")
        sb.appendLine("    Vehicle:         ${entity.vehicle?.let { "ID=${it.id} type=${it.javaClass.simpleName}" } ?: "none"}")
        val passengers = entity.passengers
        if (passengers.isEmpty()) {
            sb.appendLine("    Passengers:      none")
        } else {
            sb.appendLine("    Passengers (${passengers.size}):")
            appendPassengers(sb, passengers, "      ")
        }

        if (entity is TextDisplay) {
            val text = entity.text.string
            sb.appendLine("  [TEXT DISPLAY]")
            sb.appendLine("    Text:            $text")
            sb.appendLine("    IsPetNametag:    ${isPetNametag(text)}")
        }

        if (entity is Interaction) {
            sb.appendLine("  [INTERACTION]")
            sb.appendLine("    Width:           ${entity.bbWidth}")
            sb.appendLine("    Height:          ${entity.bbHeight}")
        }

        sb.appendLine()
        return sb.toString()
    }

    private fun appendPassengers(sb: StringBuilder, passengers: List<Entity>, indent: String) {
        for (p in passengers) {
            sb.appendLine("${indent}ID=${p.id} class=${p.javaClass.simpleName} type=${BuiltInRegistries.ENTITY_TYPE.getKey(p.type)} inPetIds=${p.id in PetEntityTracker.petEntityIds}")
            if (p.passengers.isNotEmpty()) {
                appendPassengers(sb, p.passengers, "$indent  ")
            }
        }
    }

    // Helper to check if a nametag contains pet-specific icons or text.
    private fun isPetNametag(text: String): Boolean {
        // Strict check: The "Pet" tag sequence (U+E03F, U+E034, U+E043) found in pet nametags.
        if (text.contains("\uE03F\uE034\uE043")) return true
        
        // Fallback for some pets that might use other symbols, 
        // but strictly EXCLUDE symbols used by NPCs like E055 (inspect) or E050.
        // Also require it to be multiline to avoid hitting single-line NPC titles.
        if (text.contains("\n")) {
            for (c in text) {
                if (c in '\uE051'..'\uE054' || c in '\uE056'..'\uE05F') return true
            }
        }
        
        return false
    }

    private fun buildAnnotations(client: Minecraft): Map<Int, String> {
        val level = client.level ?: return emptyMap()
        val player = client.player ?: return emptyMap()
        val annotations = mutableMapOf<Int, String>()
        val searchBox = player.boundingBox.inflate(searchRadius)

        val interactions = level.getEntitiesOfClass(Interaction::class.java, searchBox) { true }

        for (interaction in interactions) {
            if (interaction.bbWidth < 0.2f || interaction.bbWidth > 1.2f || interaction.bbHeight < 0.4f || interaction.bbHeight > 2.5f) {
                annotations[interaction.id] = "IGNORED — Dimension mismatch (${fmt(interaction.bbWidth.toDouble())}x${fmt(interaction.bbHeight.toDouble())})"
                continue
            }

            val interactionAge = interaction.tickCount
            val clusterBox = interaction.boundingBox.inflate(2.0, 5.0, 2.0)

            val nearby = level.getEntities(interaction, clusterBox) { 
                it.id != interaction.id && 
                it !is Player && 
                kotlin.math.abs(it.tickCount - interactionAge) <= 2
            }

            val actualPetParts = nearby.filter {
                it !is TextDisplay || isPetNametag(it.text.string)
            }

            val inLobby = player.x > 18000.0 && player.z < 0.0
            if (!inLobby) {
                if (actualPetParts.none { it is TextDisplay && isPetNametag(it.text.string) }) {
                    annotations[interaction.id] = "MISSED — Valid cluster but lacks a pet nametag with strict signature (E03F E034 E043) or multiline icons"
                    continue
                }
            } else {
                if (actualPetParts.isEmpty()) {
                    annotations[interaction.id] = "MISSED — Empty cluster"
                    continue
                }
            }

            annotations[interaction.id] = "DETECTED — Hitbox (age=$interactionAge, parts=${actualPetParts.size})"

            for (entity in actualPetParts) {
                val reason = if (entity is TextDisplay) "Age+Signature match" else "Age match $interactionAge"
                annotations[entity.id] = "DETECTED — Component ($reason, interaction=${interaction.id})"
                annotatePassengersRecursive(entity, annotations, interaction.id)
            }
        }

        // 3. Fallback: Identify isolated pet nametags (e.g. in Lobby where Interaction hitboxes might be missing)
        val nametags = level.getEntitiesOfClass(TextDisplay::class.java, searchBox) { isPetNametag(it.text.string) }
        for (tag in nametags) {
            if (annotations.containsKey(tag.id)) continue // skip if already found by interaction hitbox
            
            annotations[tag.id] = "DETECTED — Fallback (Isolated Pet Nametag)"
            var root: Entity = tag
            while (root.vehicle != null && root.vehicle !is Player) {
                root = root.vehicle!!
                annotations[root.id] = "DETECTED — Fallback Vehicle of ${tag.id}"
            }
            if (root !is Player) {
                annotatePassengersRecursive(root, annotations, -1)
            }
        }

        return annotations
    }

    private fun annotatePassengersRecursive(entity: Entity, annotations: MutableMap<Int, String>, interactionId: Int) {
        for (passenger in entity.passengers) {
            annotations[passenger.id] = "DETECTED — Passenger of component ${entity.id} (interaction=$interactionId)"
            annotatePassengersRecursive(passenger, annotations, interactionId)
        }
    }

    private fun writeFile(client: Minecraft) {
        val player = client.player ?: return
        val now = LocalDateTime.now()
        val timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        
        val debugFolder = File(client.gameDirectory, "wynnhidepet-debugs")
        if (!debugFolder.exists()) {
            debugFolder.mkdirs()
        }
        val file = File(debugFolder, "wynnhidepet-debug-$timestamp.txt")

        val annotations = buildAnnotations(client)

        file.bufferedWriter().use { w ->
            w.appendLine("WynnHidePet Debug Dump")
            w.appendLine("Generated:  ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            w.appendLine("Player:     ${player.name.string}")
            w.appendLine("Position:   x=${fmt(player.x)}, y=${fmt(player.y)}, z=${fmt(player.z)}")
            
            val level = client.level
            if (level != null) {
                w.appendLine("Level ID:   ${level.dimension()}")
                w.appendLine("Biomes:     ${level.getBiome(player.blockPosition()).unwrapKey().map { it.toString() }.orElse("unknown")}")
            }
            
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
