package dev.ilgax.wynnhidepet.client

import com.mojang.brigadier.arguments.IntegerArgumentType
import dev.ilgax.wynnhidepet.getConfig
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.entity.Display
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

    private const val DEFAULT_RADIUS = 8

    private var ticksRemaining = 0   // >0: collecting, <0: visual-only ring, 0: inactive
    private var searchRadius = DEFAULT_RADIUS.toDouble()
    private val entitySnapshots = LinkedHashMap<Int, String>()
    private val packetLog = ArrayList<String>()
    private var localTick = 0

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("whp")
                    .then(
                        literal("debug")
                            .requires { getConfig().enableDebugTools }
                            .executes { ctx ->
                                beginInstant(ctx.source, DEFAULT_RADIUS.toDouble())
                                1
                            }
                            .then(
                                literal("end")
                                    .executes { ctx ->
                                        if (ticksRemaining > 0) finishTimed(ctx.source.client)
                                        else ctx.source.sendFeedback(Component.literal("§cNo debug session is running."))
                                        1
                                    }
                            )
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

        // Entities appearing and disappearing (e.g. a pet re-sent after falling behind) during a capture.
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ -> recordEntity("LOAD", entity) }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> recordEntity("UNLOAD", entity) }
    }

    private fun recordEntity(event: String, entity: Entity) {
        if (ticksRemaining <= 0 || entity is Player) return
        val player = Minecraft.getInstance().player ?: return
        if (player.distanceTo(entity) > searchRadius) return
        recordPacket {
            "$event ${entity.id} ${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).path} " +
                "pos=${fmt(entity.x)},${fmt(entity.y)},${fmt(entity.z)} self=${fmt(player.distanceTo(entity).toDouble())}"
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
            if (ticksRemaining == 0) finishTimed(client)
        }
    }

    private fun finishTimed(client: Minecraft) {
        ticksRemaining = 0
        val file = writeFile(client)
        client.player?.displayClientMessage(
            Component.literal("§aDebug session complete. Written to ").append(fileLink(file)),
            false
        )
    }

    private fun beginInstant(source: FabricClientCommandSource, radius: Double) {
        entitySnapshots.clear()
        searchRadius = radius
        ticksRemaining = 0
        collectSnapshot(source.client)
        val file = writeFile(source.client)
        source.sendFeedback(
            Component.literal("§aSnapshot written to ").append(fileLink(file))
                .append(Component.literal(" §a(radius ${radius.toInt()}b)"))
        )
        ticksRemaining = -60 // show ring for exactly 3 seconds
    }

    /** Seconds left in the running timed capture, or null if none is running. */
    val captureSecondsLeft: Int?
        get() = if (ticksRemaining > 0) (ticksRemaining + 19) / 20 else null

    /** Records a sound/particle packet while a timed capture is running; [line] is only built then. */
    fun recordPacket(line: () -> String) {
        if (ticksRemaining > 0) packetLog.add("t=$localTick ${line()}")
    }

    private fun beginTimed(source: FabricClientCommandSource, seconds: Int, radius: Double) {
        entitySnapshots.clear()
        packetLog.clear()
        lastTexts.clear()
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
            // Nametags can be blanked or rewritten while their entity stays; log each change with its tick.
            if (entity is TextDisplay && ticksRemaining > 0) {
                val text = entity.text.string
                val previous = lastTexts.put(entity.id, text)
                if (previous != text) {
                    recordPacket { "TEXT ${entity.id} pet=${PetEntityTracker.isPetNametag(text)} '${text.replace("\n", "\\n")}'" }
                }
            }
        }
    }

    private val lastTexts = HashMap<Int, String>()

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
        sb.appendLine("    IsInvisible:     ${entity.isInvisible}")

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
            sb.appendLine("    IsPetNametag:    ${PetEntityTracker.isPetNametag(text)}")
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

    private fun buildAnnotations(client: Minecraft): Map<Int, String> {
        val level = client.level ?: return emptyMap()
        val player = client.player ?: return emptyMap()
        val annotations = mutableMapOf<Int, String>()
        val searchBox = player.boundingBox.inflate(searchRadius)

        for (pet in PetEntityTracker.findPets(level, searchBox)) {
            for (id in pet.ids) {
                annotations[id] = "DETECTED — Part of pet ${pet.anchorId} (${pet.reason}, ${pet.ids.size} entities)"
            }
            // A badge nametag with no recognised layout around it means Wynncraft built this pet differently.
            if (pet.ids.size == 1) {
                annotations[pet.anchorId] = "MISSED — Pet nametag, but no known model layout around its ID"
            }
        }

        return annotations
    }

    private fun writeFile(client: Minecraft): File? {
        val player = client.player ?: return null
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

            if (packetLog.isNotEmpty()) {
                w.appendLine("── SOUND / PARTICLE PACKETS (${packetLog.size}) ───────────────────")
                packetLog.forEach { w.appendLine("  $it") }
                w.appendLine("=".repeat(60))
                w.newLine()
            }

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
        packetLog.clear()
        return file
    }

    /** The file's name, underlined; clicking it opens the file. */
    private fun fileLink(file: File?): Component {
        if (file == null) return Component.literal("§cnothing (no player)")
        return Component.literal(file.name).withStyle { style ->
            style.withUnderlined(true)
                .withClickEvent(ClickEvent.OpenFile(file))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Open ${file.absolutePath}")))
        }
    }

    private fun fmt(d: Double) = String.format("%.3f", d)
}
