plugins {
    kotlin("jvm")
    id("maven-publish")
}

mavenPublicationConfig {
    displayName = "Compose Preview RPC utils"
    description = "Compose Preview RPC utils"
    artifactId = "preview-rpc"
}

dependencies {
    implementation(kotlin("stdlib"))
}