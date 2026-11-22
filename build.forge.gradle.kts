// Forge from 1.20.2 on runs on Mojang names, so the compiled jar ships as is.
plugins {
    java
    id("net.minecraftforge.gradle") version "[7.0.3,8)"
}

val node = project.name
val target = MusicBoxTarget(sc.current.version, "forge", { sc.current.parsed >= it }, { runCatching { sc.properties[it] }.getOrNull() })

musicBox(target, mapOf(
    "loader_range" to target.prop("deps.forge_range"),
    "forge_range" to target.prop("deps.forge_range"),
))
shipJLayer(target)

minecraft {
    mappings("official", sc.current.version)

    runs {
        configureEach {
            workingDir.set(rootProject.layout.projectDirectory.dir("run/$node"))
            jvmArgs("-Xmx3G")
            mods {
                register(target.modId) {
                    source(sourceSets.main.get())
                }
            }
        }
        register("client")
        register("server") {
            args("--nogui")
        }
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${sc.current.version}-${target.prop("deps.forge")}"))
}

collectJar(target, tasks.jar.flatMap { it.archiveFile })
