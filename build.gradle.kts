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
    // Mines module: add FAWE/WorldEdit compileOnly deps here, e.g.
    // compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:<version>") { isTransitive = false }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveFileName.set("SpacepunkPrison.jar")
}
