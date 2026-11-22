import dev.kikugie.stonecutter.controller.task.StonecutterTaskMutexService

// Forge up to 1.20.1 runs on SRG names, so the release jar is the reobfuscated one.
plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.148"
}

val node = project.name
val target = MusicBoxTarget(sc.current.version, "forge", { sc.current.parsed >= it }, { runCatching { sc.properties[it] }.getOrNull() })

musicBox(target, mapOf(
    "loader_range" to target.prop("deps.forge_range"),
    "forge_range" to target.prop("deps.forge_range"),
))
shipJLayer(target)

repositories {
    mavenCentral()
}

legacyForge {
    version = "${sc.current.version}-${target.prop("deps.forge")}"

    mods {
        register(target.modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        configureEach {
            gameDirectory = rootProject.file("run/$node")
            jvmArgument("-Xmx3G")
        }
        register("client") {
            client()
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
    }
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
    usesService(StonecutterTaskMutexService.create(gradle, name))
}

collectJar(target, tasks.named("reobfJar").map { it.outputs.files.singleFile })
