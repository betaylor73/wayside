plugins {
    id("java")
}

group = "com.questrail"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Netty is currently used only by the Netty-backed UDP transport adapter
    // under src/main/java/com/questrail/wayside/protocol/genisys/transport/udp/netty.
    // If that adapter is not used, this dependency can be removed or relocated.
    implementation("io.netty:netty-all:4.1.111.Final")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}