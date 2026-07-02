plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.alexshamrai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("com.google.api-client:google-api-client:2.8.0")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20250603-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.48.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 modularization: the MockMvc<->Spring Security test bridge lives in its own starter
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveBaseName = "music-cat"
}

// Build the React frontend and package it into the jar's static/ resources
tasks.processResources {
    dependsOn(":frontend:npmBuild")
    from(project(":frontend").layout.projectDirectory.dir("dist")) {
        into("static")
    }
}