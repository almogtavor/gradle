plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "API extraction for Java"

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":hashing"))
    implementation(project(":files"))
    implementation(project(":snapshots"))

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    testImplementation(project(":base-services"))
    testImplementation("org.gradle:internal-testing")
}