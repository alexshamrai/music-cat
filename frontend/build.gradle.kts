plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version = "22.12.0"
    download = true
}

val npmBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "build")
    inputs.dir("src")
    inputs.dir("public")
    inputs.files("package.json", "package-lock.json", "vite.config.ts",
        "index.html", "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json")
    outputs.dir("dist")
}
