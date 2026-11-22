import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources

private val LOADERS = listOf("fabric", "forge", "neoforge")

/** One Stonecutter node: a Minecraft version built for one loader. */
class MusicBoxTarget(
    val minecraft: String,
    val loader: String,
    private val atLeast: (String) -> Boolean,
    private val property: (String) -> String?,
) {
    fun atLeast(version: String): Boolean = atLeast.invoke(version)

    fun prop(key: String): String = property(key) ?: error("$minecraft-$loader is missing $key in stonecutter.properties.toml")

    val forgeLike: Boolean get() = loader != "fabric"

    /** The Java release Mojang ships with this Minecraft version. */
    val java: Int
        get() = when {
            atLeast("26.1") -> 25
            atLeast("1.20.5") -> 21
            atLeast("1.18") -> 17
            atLeast("1.17") -> 16
            else -> 8
        }

    val modId: String get() = prop("mod.id")
    val modVersion: String get() = prop("mod.version")
}

/**
 * Shared setup for every node. The loader build scripts only add what their Gradle plugin
 * needs; naming, Java level, resource rewriting and the release jar layout all live here.
 */
fun Project.musicBox(target: MusicBoxTarget, loaderProps: Map<String, String>) {
    group = target.prop("mod.group")
    version = "${target.minecraft}-${target.loader}-${target.modVersion}"
    extensions.configure<BasePluginExtension> { archivesName.set(target.modId) }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(target.java))
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(target.java)
    }

    val main = extensions.getByType<SourceSetContainer>().getByName("main")
    main.java {
        for (other in LOADERS - target.loader) {
            exclude("com/musicbox/platform/$other/**")
        }
    }
    for (overlay in listOfNotNull(target.loader, "forgelike".takeIf { target.forgeLike })) {
        val dir = rootProject.file("src/overlays/$overlay")
        if (dir.isDirectory) {
            main.resources.srcDir(dir)
        }
    }

    tasks.named<ProcessResources>("processResources") {
        val props = mapOf(
            "mod_id" to target.modId,
            "mod_name" to target.prop("mod.name"),
            "mod_version" to target.modVersion,
            "mod_authors" to target.prop("mod.authors"),
            "mod_license" to target.prop("mod.license"),
            "mod_description" to target.prop("mod.description"),
            "mod_description_json" to target.prop("mod.description").replace("\n", "\\n"),
            "minecraft_range" to target.prop("mod.mc_compat"),
            "java" to target.java.toString(),
            "pack_format" to target.prop("pack.format"),
        ) + loaderProps
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "fabric.mod.json", "pack.mcmeta")) {
            expand(props)
        }
        // Filters match on the source path, so they are registered before the renames below.
        if (!target.atLeast("1.20.5")) {
            filesMatching("data/*/recipe/*.json") {
                filter { line -> line.replace("\"id\":", "\"item\":") }
            }
        }
        eachFile { path = resourcePath(target, path) }
        includeEmptyDirs = false
    }

    tasks.named<Jar>("jar") {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        manifest.attributes(
            mapOf(
                "Specification-Title" to target.modId,
                "Specification-Vendor" to target.prop("mod.authors"),
                "Specification-Version" to "1",
                "Implementation-Title" to target.prop("mod.name"),
                "Implementation-Version" to target.modVersion,
                "Implementation-Vendor" to target.prop("mod.authors"),
            )
        )
    }
}

/**
 * Resources are kept in their 1.21 layout. Older versions get them moved to where that
 * version looks for them.
 */
private fun resourcePath(target: MusicBoxTarget, path: String): String {
    var result = path
    if (!target.atLeast("1.20.5") && result == "assets/musicboxradio/textures/models/armor/headphones_layer_1.png") {
        // Before armor materials were registered, the worn texture was looked up by material
        // name in the vanilla namespace.
        result = "assets/minecraft/textures/models/armor/musicboxradio_headphones_layer_1.png"
    }
    if (!target.atLeast("1.21")) {
        result = result
            .replace(Regex("^data/([^/]+)/(recipe|loot_table)/")) { "data/${it.groupValues[1]}/${it.groupValues[2]}s/" }
            .replace(Regex("^data/([^/]+)/tags/(block|item)/")) { "data/${it.groupValues[1]}/tags/${it.groupValues[2]}s/" }
    }
    if (target.loader == "neoforge" && !target.atLeast("1.20.5") && result == "META-INF/neoforge.mods.toml") {
        result = "META-INF/mods.toml"
    }
    return result
}

/**
 * Unpacks JLayer into the mod's own classes directory on Forge and NeoForge.
 *
 * Dev runs boot from that directory and the mod classloader cannot reach a plain classpath
 * library, so merging only at jar time leaves the decoder thread throwing
 * NoClassDefFoundError. It stays compile-only: on the runtime classpath as well, the loader
 * turns the jar into a second module exporting the same packages and refuses to boot.
 */
fun Project.shipJLayer(target: MusicBoxTarget) {
    val shipped = configurations.create("shipped")
    configurations.getByName("compileOnly").extendsFrom(shipped)
    dependencies.add("shipped", "javazoom:jlayer:${target.prop("deps.jlayer")}")

    val main = extensions.getByType<SourceSetContainer>().getByName("main")
    // One output directory, or FML discovers the mod twice and dies on duplicate packages.
    main.output.setResourcesDir(main.java.classesDirectory)

    val extract = tasks.register<Copy>("extractShipped") {
        dependsOn("compileJava")
        from(provider { shipped.map { if (it.isDirectory) it else zipTree(it) } })
        into(main.java.classesDirectory)
        exclude("META-INF/**", "module-info.class")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    tasks.named("classes") { dependsOn(extract) }
}

/** Copies the jar players install into build/libs/{mod version}/ at the root. */
fun Project.collectJar(target: MusicBoxTarget, jar: Any) {
    tasks.register<Copy>("collectJar") {
        group = "build"
        description = "Copies the release jar into the root build/libs/${target.modVersion}/"
        from(jar)
        into(rootProject.layout.buildDirectory.dir("libs/${target.modVersion}"))
    }
}
