plugins {
    // Applies the Loom variant that matches the Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

val node = project.name
val target = MusicBoxTarget(sc.current.version, "fabric", { sc.current.parsed >= it }, { runCatching { sc.properties[it] }.getOrNull() })

musicBox(target, mapOf(
    "loader_range" to ">=${target.prop("deps.fabric_loader")}",
))

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${target.prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${target.prop("deps.fabric_api")}")

    // Pure-Java MP3 decoder, jar-in-jar'd so players do not have to install it.
    implementation("javazoom:jlayer:${target.prop("deps.jlayer")}")
    include("javazoom:jlayer:${target.prop("deps.jlayer")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/overlays/fabric/fabric.mod.json")

    runConfigs.all {
        runDirectory = rootProject.file("run/$node")
        vmArgs("-Xmx3G")
    }
}

collectJar(target, loomx.modJar.flatMap { it.archiveFile })
