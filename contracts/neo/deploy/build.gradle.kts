val neow3jVersion: String by project

plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass = "com.smartargs.mempolis.deploy.DeployCli"
}

dependencies {
    implementation("io.neow3j:contract:$neow3jVersion")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.9")
    testImplementation(project(":testkit"))
    testImplementation(project(":guestbook"))
}
