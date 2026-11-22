import dev.kikugie.stonecutter.controller.task.StonecutterTaskMutexService

plugins {
    id("net.neoforged.moddev") version "2.0.148"
}

val node = project.name
val target = MusicBoxTarget(sc.current.version, "neoforge", { sc.current.parsed >= it }, { runCatching { sc.properties[it] }.getOrNull() })

musicBox(target, mapOf(
    "loader_range" to "[1,)",
    "neoforge_range" to target.prop("deps.neoforge_range"),
))
shipJLayer(target)

repositories {
    mavenCentral()
}

neoForge {
    version = target.prop("deps.neoforge")

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

collectJar(target, tasks.jar.flatMap { it.archiveFile })
