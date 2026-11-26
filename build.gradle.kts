import java.security.MessageDigest

plugins {
    java
}

group = "dev.spacepunk"
version = "0.1.0-SNAPSHOT"

// Paper for Minecraft 26.3. Check https://repo.papermc.io for newer builds.
val paperApi = "26.3.build.262-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    // Mines module: optional FAWE refiller (used only when FastAsyncWorldEdit is installed on the server).
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.16.3") { isTransitive = false }
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.16.3") { isTransitive = false }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

// Resource pack workstream: zip /resourcepack reproducibly (stable SHA-1 for identical sources) and embed it
// in the jar as pack/spacepunk-pack.zip. CI also uploads the zip and its SHA-1.
val resourcePackZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Zips the server resource pack from /resourcepack."
    from(layout.projectDirectory.dir("resourcepack")) {
        exclude("README.md")
    }
    archiveFileName.set("spacepunk-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("resourcepack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    doLast {
        val zip = archiveFile.get().asFile
        val sha1 = MessageDigest.getInstance("SHA-1").digest(zip.readBytes())
            .joinToString("") { b -> "%02x".format(b) }
        File(zip.parentFile, "spacepunk-pack.zip.sha1").writeText(sha1 + "\n")
        logger.lifecycle("Resource pack: ${zip.length() / 1024} KiB, sha1 $sha1")
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
    from(resourcePackZip) { into("pack") }
}

tasks.jar {
    archiveFileName.set("SpacepunkPrison.jar")
}
