/*
 *
 *  * This file is part of Duels-Plugin - https://github.com/goncalodelima/Duels-Plugin
 *  * Copyright (c) 2026 goncalodelima and contributors
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1"
    id("com.gradleup.shadow") version "9.0.0-rc1"
}

tasks {

    named<ShadowJar>("shadowJar"){
        relocate("co.aikar.commands", "pt.gongas.box.lib.aikar.commands")
        relocate("co.aikar.locales", "pt.gongas.box.lib.aikar.locales")
        exclude("META-INF/versions/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    build {
        dependsOn(shadowJar)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.triumphteam.dev/snapshots") // GUI API
    maven("https://repo.aikar.co/content/groups/aikar/") // Aikar
    maven("https://repo.purpurmc.org/snapshots") // Purpur
    maven("https://repo.papermc.io/repository/maven-public/") // paperweight, Velocity
    maven("https://repo.codemc.org/repository/nms/") // CraftBukkit + NMS
    maven("https://repo.codemc.org/repository/maven-public/") // Item-NBT-API
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-releases/") // PacketEventsAPI
    maven("https://repo.codemc.io/repository/maven-snapshots/") // PacketEventsAPI
    maven("https://mvn.lumine.io/repository/maven-public/") // MythicMobs
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/") // AdvancedSlimePaper
    maven("https://repo.codemc.io/repository/goncalodelima/") // Economy-Plugin
    maven("https://repo.william278.net/releases") // HuskHomes
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

group = "pt.gongas"
version = "1.0.0"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    compileOnly("net.kyori:adventure-text-minimessage:4.24.0")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT")
    compileOnly("com.infernalsuite.asp:api:4.1.0-SNAPSHOT")
    compileOnly("pt.gongas:EconomyPlugin-paper:1.3.0")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    compileOnly("net.william278.huskhomes:huskhomes-bukkit:4.10")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

bukkit {
    name = "duels-plugin"
    version = "${project.version}"
    main = "pt.gongas.duel.DuelPlugin"
    depend = listOf("redis-plugin", "HuskHomes")
    author = "ReeachyZ_"
    website = "https://github.com/goncalodelima"
    description = "Duels Plugin"
    apiVersion = "1.21"
    commands {
        register("duels") {
            aliases = listOf("duel", "x1")
        }
    }

}