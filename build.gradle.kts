plugins {
    id("java")
}

group = "com.vladomeme"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.vladomeme.Main"
    }
}