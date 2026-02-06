plugins {
    id("java")
    `maven-publish`
}

group = "cc.getportal"
version = "0.2.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "cc.getportal"
            artifactId = "portal-java-sdk"
            version = "0.2.1"
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // etBrains annotations (compile-time only — doesn’t get packaged)
    compileOnly("org.jetbrains:annotations:26.0.2")

    // SLF4J API and simple backend
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    // WebSocket Client
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    // Json serialization
    implementation("com.google.code.gson:gson:2.13.2")

//    implementation("com.google.zxing:core:3.3.0")
//    implementation("com.google.zxing:javase:3.3.0")

}

tasks.test {
    useJUnitPlatform()
}