val neow3jVersion: String by project

plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api("io.neow3j:devpack:$neow3jVersion")
    api("io.neow3j:devpack-test:$neow3jVersion")
    api("io.neow3j:contract:$neow3jVersion")
    api("io.neow3j:compiler:$neow3jVersion")
    api("org.junit.jupiter:junit-jupiter:5.10.2")
    api("org.assertj:assertj-core:3.25.3")
    runtimeOnly("org.junit.platform:junit-platform-launcher")
}
