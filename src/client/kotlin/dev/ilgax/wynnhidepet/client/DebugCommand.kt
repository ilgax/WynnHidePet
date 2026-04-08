package dev.ilgax.wynnhidepet.client

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.entity.decoration.DisplayEntity.*
import net.minecraft.entity.decoration.InteractionEntity
import net.minecraft.entity.mob.MobEntity
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
                            // /whp debug
                            .executes { ctx ->
                                beginInstant(ctx.source, DEFAULT_RADIUS.toDouble())
                                1
                            }
                            .then(
                                argument("seconds", IntegerArgumentType.integer(0, 300))
                                    // /whp debug <seconds>   (0 = instant)
                                    .executes { ctx ->
                                        val s = IntegerArgumentType.getInteger(ctx, "seconds")
                                        if (s == 0) beginInstant(ctx.source, DEFAULT_RADIUS.toDouble())
                                        else beginTimed(ctx.source, s, DEFAULT_RADIUS.toDouble())
                                        1
                                    }
                                    .then(
                                        argument("range", IntegerArgumentType.integer(1, 512))
                                            // /whp debug <seconds> <range>   (seconds=0 = instant)
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

        if (ticksRemaining <= 0) return
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

    private fun beginInstant(source: FabricClientCommandSource, radius: Double) {
        entitySnapshots.clear()
        searchRadius = radius
        ticksRemaining = 0
        collectSnapshot(source.client)
        writeFile(source.client)
        source.sendFeedback(Text.literal("§aSnapshot written to wynnhidepet-debug-*.txt (radius ${radius.toInt()}b)"))
        ticksRemaining = -60 // show ring for ~3 seconds after instant snapshot
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
            entitySnapshots[entity.id] = snapshotEntity(entity, player, client)
        }
    }

    // Spawn a horizontal ring of particles at the search radius.
    // Particles are client-side only and are not entities — they won't appear in the dump.
    // Active session: END_ROD (white). Post-instant visual: HAPPY_VILLAGER (green).
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

        if (ticksRemaining < 0) ticksRemaining++
    }

    private fun snapshotEntity(entity: Entity, player: net.minecraft.entity.player.PlayerEntity, client: MinecraftClient): String {
        val sb = StringBuilder()
        val dx = entity.x - player.x
        val dy = entity.y - player.y
        val dz = entity.z - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        sb.appendLine("╔══════════════════════════════════════════════════════════")
        sb.appendLine("║ Entity ID: ${entity.id}   Distance: ${fmt(dist)} blocks")
        sb.appendLine("╚══════════════════════════════════════════════════════════")

        // ── [BASE] ──────────────────────────────────────────────────────────
        sb.appendLine("  [BASE]")
        sb.appendLine("    Class (simple):  ${entity.javaClass.simpleName}")
        sb.appendLine("    Class (full):    ${entity.javaClass.name}")
        sb.appendLine("    Registry type:   ${Registries.ENTITY_TYPE.getId(entity.type)}")
        sb.appendLine("    UUID:            ${entity.uuid}")
        sb.appendLine("    Age (ticks):     ${entity.age}")
        sb.appendLine("    Position:        x=${fmt(entity.x)}, y=${fmt(entity.y)}, z=${fmt(entity.z)}")
        sb.appendLine("    Velocity:        dx=${fmt(entity.velocity.x)}, dy=${fmt(entity.velocity.y)}, dz=${fmt(entity.velocity.z)}")
        sb.appendLine("    BoundingBox:     ${entity.boundingBox}")
        sb.appendLine("    Width:           ${entity.width}")
        sb.appendLine("    Height:          ${entity.height}")
        sb.appendLine("    Yaw/Pitch:       yaw=${fmt(entity.yaw.toDouble())}, pitch=${fmt(entity.pitch.toDouble())}")

        // ── [NAMES] ─────────────────────────────────────────────────────────
        sb.appendLine("  [NAMES]")
        sb.appendLine("    Name:            ${entity.name.string}")
        sb.appendLine("    DisplayName:     ${entity.displayName?.string ?: "none"}")
        sb.appendLine("    CustomName:      ${entity.customName?.string ?: "none"}")
        sb.appendLine("    CustomNameVis:   ${entity.isCustomNameVisible}")
        sb.appendLine("    ScoreboardName:  ${entity.nameForScoreboard}")

        // ── [FLAGS] ─────────────────────────────────────────────────────────
        sb.appendLine("  [FLAGS]")
        sb.appendLine("    IsInvisible:     ${entity.isInvisible}")
        sb.appendLine("    IsGlowing:       ${entity.isGlowing}")
        sb.appendLine("    IsOnFire:        ${entity.isOnFire}")
        sb.appendLine("    IsSilent:        ${entity.isSilent}")
        sb.appendLine("    IsSneaking:      ${entity.isSneaking}")
        sb.appendLine("    IsSprinting:     ${entity.isSprinting}")
        sb.appendLine("    IsSwimming:      ${entity.isSwimming}")
        sb.appendLine("    IsTouchingWater: ${entity.isTouchingWater}")
        sb.appendLine("    IsOnGround:      ${entity.isOnGround}")
        sb.appendLine("    NoGravity:       ${entity.hasNoGravity()}")
        sb.appendLine("    NoClip:          ${entity.noClip}")

        // ── [SCOREBOARD] ────────────────────────────────────────────────────
        sb.appendLine("  [SCOREBOARD]")
        val team = client.world?.scoreboard?.getScoreHolderTeam(entity.nameForScoreboard)
        if (team != null) {
            sb.appendLine("    Team name:       ${team.name}")
            sb.appendLine("    Team color:      ${team.color}")
            sb.appendLine("    Team prefix:     ${team.prefix.string}")
            sb.appendLine("    Team suffix:     ${team.suffix.string}")
            sb.appendLine("    NameTagVis:      ${team.nameTagVisibilityRule}")
            sb.appendLine("    CollisionRule:   ${team.collisionRule}")
        } else {
            sb.appendLine("    Team:            none")
        }

        // ── [TAGS] ──────────────────────────────────────────────────────────
        sb.appendLine("  [TAGS]")
        val tags = entity.commandTags
        if (tags.isEmpty()) {
            sb.appendLine("    CommandTags:     none")
        } else {
            tags.forEach { sb.appendLine("    Tag:             $it") }
        }

        // ── [HIERARCHY] ─────────────────────────────────────────────────────
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

        // ── [DISPLAY (shared)] ──────────────────────────────────────────────
        if (entity is DisplayEntity) {
            sb.appendLine("  [DISPLAY (shared)]")
            sb.appendLine("    InterpDuration:  ${entity.interpolationDuration}")
            sb.appendLine("    ViewRange:       ${entity.viewRange}")
            sb.appendLine("    ShadowRadius:    ${entity.shadowRadius}")
            sb.appendLine("    ShadowStrength:  ${entity.shadowStrength}")
            sb.appendLine("    GlowColorOver:   ${entity.glowColorOverride}")
        }

        // ── [ITEM DISPLAY] ──────────────────────────────────────────────────
        when (entity) {
            is ItemDisplayEntity -> {
                sb.appendLine("  [ITEM DISPLAY]")
                sb.appendLine("    ItemStack:       ${entity.itemStack}")
                sb.appendLine("    ItemId:          ${Registries.ITEM.getId(entity.itemStack.item)}")
                sb.appendLine("    ItemCount:       ${entity.itemStack.count}")
                sb.appendLine("    ItemComponents:  ${entity.itemStack.components}")
            }
            is TextDisplayEntity -> {
                val text = entity.text.string
                val codepoints = buildString {
                    text.codePoints().forEach { cp ->
                        append("U+${cp.toString(16).uppercase().padStart(4, '0')} ")
                    }
                }.trim()
                sb.appendLine("  [TEXT DISPLAY]")
                sb.appendLine("    Text:            $text")
                sb.appendLine("    Codepoints:      $codepoints")
                sb.appendLine("    HasHighSurrogate:${text.any { it.isHighSurrogate() }}")
                sb.appendLine("    HasBmpPua:       ${text.any { it in '\uE000'..'\uF8FF' }}")
                sb.appendLine("    TextOpacity:     ${entity.textOpacity}")
                sb.appendLine("    Background:      ${entity.background}")
            }
            is BlockDisplayEntity -> {
                sb.appendLine("  [BLOCK DISPLAY]")
                sb.appendLine("    BlockState:      ${entity.blockState}")
            }
            is InteractionEntity -> {
                sb.appendLine("  [INTERACTION]")
                sb.appendLine("    Width:           ${entity.width}")
                sb.appendLine("    Height:          ${entity.height}")
            }
        }

        // ── [LIVING] ────────────────────────────────────────────────────────
        if (entity is LivingEntity) {
            sb.appendLine("  [LIVING]")
            sb.appendLine("    Health:          ${entity.health}/${entity.maxHealth}")
            sb.appendLine("    AbsorptionAmt:   ${entity.absorptionAmount}")
            sb.appendLine("    ArmorValue:      ${entity.armor}")
            sb.appendLine("    IsDead:          ${entity.isDead}")
            sb.appendLine("    HurtTime:        ${entity.hurtTime}")
            sb.appendLine("    StuckArrows:     ${entity.stuckArrowCount}")
            sb.appendLine("    FallDistance:    ${fmt(entity.fallDistance)}")
            val effects = entity.activeStatusEffects
            if (effects.isEmpty()) {
                sb.appendLine("    StatusEffects:   none")
            } else {
                sb.appendLine("    StatusEffects (${effects.size}):")
                effects.forEach { (type, effect) ->
                    sb.appendLine("      - ${Registries.STATUS_EFFECT.getId(type.value())} amp=${effect.amplifier} dur=${effect.duration}t")
                }
            }
        }

        // ── [MOB] ───────────────────────────────────────────────────────────
        if (entity is MobEntity) {
            sb.appendLine("  [MOB]")
            sb.appendLine("    AIDisabled:      ${entity.isAiDisabled}")
            sb.appendLine("    IsPersistent:    ${entity.isPersistent}")
            sb.appendLine("    CanPickupLoot:   ${entity.canPickUpLoot()}")
            sb.appendLine("    IsLeashed:       ${entity.isLeashed}")
            val equipped = EquipmentSlot.entries.mapNotNull { slot ->
                val stack = entity.getEquippedStack(slot)
                if (!stack.isEmpty) "$slot -> $stack (${Registries.ITEM.getId(stack.item)})" else null
            }
            if (equipped.isEmpty()) {
                sb.appendLine("    Equipment:       none")
            } else {
                sb.appendLine("    Equipment:")
                equipped.forEach { sb.appendLine("      $it") }
            }
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

    // Run the same detection logic as PetEntityTracker and return a per-entity annotation.
    // Annotations explain exactly why each entity was detected, missed, or is unrelated.
    private fun buildAnnotations(client: MinecraftClient): Map<Int, String> {
        val world = client.world ?: return emptyMap()
        val player = client.player ?: return emptyMap()
        val annotations = mutableMapOf<Int, String>()
        val searchBox = player.boundingBox.expand(searchRadius)

        val interactions = world.getEntitiesByType(
            TypeFilter.instanceOf(InteractionEntity::class.java), searchBox) { true }

        for (interaction in interactions) {
            val nearbyBox = interaction.boundingBox.expand(2.0)

            val anchor = world.getEntitiesByType(
                TypeFilter.instanceOf(ItemDisplayEntity::class.java), nearbyBox) { true }
                .minByOrNull { val dx = it.x - interaction.x; val dz = it.z - interaction.z; dx*dx + dz*dz }

            if (anchor == null) {
                annotations[interaction.id] = "MISSED — InteractionEntity but no ItemDisplayEntity within 2 blocks"
                continue
            }

            val clusterMin = minOf(anchor.id, interaction.id)
            val clusterMax = maxOf(anchor.id, interaction.id)
            val span = clusterMax - clusterMin

            if (span > 50) {
                annotations[interaction.id] = "MISSED — cluster ID span too large ($span > 50, anchor=${anchor.id})"
                annotations[anchor.id]       = "MISSED — cluster ID span too large ($span > 50, interaction=${interaction.id})"
                continue
            }

            val clusterRange = clusterMin..clusterMax

            annotations[interaction.id] = "DETECTED — interaction (anchor=${anchor.id}, span=$span)"
            annotations[anchor.id]      = "DETECTED — anchor (interaction=${interaction.id}, span=$span)"

            anchor.passengerList.filterIsInstance<DisplayEntity>().forEach {
                annotations[it.id] = "DETECTED — passenger of anchor ${anchor.id}"
            }

            world.getEntitiesByType(TypeFilter.instanceOf(ItemDisplayEntity::class.java), nearbyBox) { true }
                .filter { it.vehicle == null && it.passengerList.isEmpty() && it.id in clusterRange }
                .forEach { annotations[it.id] = "DETECTED — standalone shadow (cluster interaction=${interaction.id})" }

            val tallBox = interaction.boundingBox.expand(2.0, 5.0, 2.0)
            world.getEntitiesByType(TypeFilter.instanceOf(TextDisplayEntity::class.java), tallBox) { true }
                .filter { it.vehicle == null && it.text.string.any { c -> c.isHighSurrogate() } && it.id in clusterRange }
                .forEach { annotations[it.id] = "DETECTED — nametag (cluster interaction=${interaction.id})" }
        }

        return annotations
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

            // Cluster analysis summary
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

            // Entity snapshots sorted by distance (distance is in the second header line)
            entitySnapshots.forEach { (id, snapshot) ->
                val annotation = annotations[id]
                if (annotation != null) {
                    // Insert [ANALYSIS] section after the ╚══ header line
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
