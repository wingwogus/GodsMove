plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
}

repositories {
    mavenCentral()
}

group = "com.godsmove"
version = "0.0.1-SNAPSHOT"

dependencies {
    api(project(":domain"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.8"))
    implementation("org.springframework.ai:spring-ai-model")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
