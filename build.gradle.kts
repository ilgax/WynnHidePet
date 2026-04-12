import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName.set(providers.gradleProperty("archives_base_name").get())
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
    
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("wynnhidepet") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}


repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    maven("https://maven.shedaniel.me/")       // Cloth Config
    maven("https://maven.terraformersmc.com/") // ModMenu
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("kotlin_loader_version").get()}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_version").get()}")
    modApi("me.shedaniel.cloth:cloth-config-fabric:${providers.gradleProperty("cloth_config_version").get()}")
    modImplementation("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")
}

tasks.processResources {
    inputs.property("version", version)
    inputs.property("minecraft_version", providers.gradleProperty("minecraft_version").get())
    inputs.property("loader_version", providers.gradleProperty("loader_version").get())
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to version,
            "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
            "loader_version" to providers.gradleProperty("loader_version").get(),
            "kotlin_loader_version" to providers.gradleProperty("kotlin_loader_version").get()
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release = 21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

val generateBuildConfig by tasks.registering {
    val isRunClient = gradle.startParameter.taskNames.any { it.contains("runClient", ignoreCase = true) }
    val debugValue = project.findProperty("wynnhidepet.debug") == "true" || isRunClient
    val outputDir = layout.buildDirectory.dir("generated/buildconfig/kotlin")
    
    inputs.property("debug", debugValue)
    outputs.dir(outputDir)
    
    doLast {
        val file = outputDir.get().file("dev/ilgax/wynnhidepet/BuildConstants.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("""
            package dev.ilgax.wynnhidepet
            
            object BuildConstants {
                const val DEBUG = $debugValue
            }
        """.trimIndent())
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBuildConfig)
}


tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = providers.gradleProperty("archives_base_name").get()
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
