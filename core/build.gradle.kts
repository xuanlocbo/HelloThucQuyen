plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
tasks.register<JavaExec>("contractCheck") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("vn.htq.core.ContractCheckKt")
    args(rootProject.file("app/src/main/assets/htq").absolutePath)
}
