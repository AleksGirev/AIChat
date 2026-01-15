plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.example.aichat"
version = "1.0.0"

dependencies {
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Serialization (updated for Kotlin 2.2.0 compatibility)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Official Kotlin MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk-client-jvm:0.8.0")
    
    // Kotlinx IO for stream handling (required by MCP SDK, updated for Kotlin 2.2.0)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    // Apache Tika for PDF and document parsing
    implementation("org.apache.tika:tika-core:2.9.0")
    implementation("org.apache.tika:tika-parsers:2.9.0")
    
    // SQLite JDBC driver (using stable version to avoid StackOverflowError)
    implementation("org.xerial:sqlite-jdbc:3.43.2.2")
}

application {
    mainClass.set("com.example.aichat.console.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// Task for running the Repo MCP Server
tasks.register<JavaExec>("runRepoMcpServer") {
    group = "application"
    description = "Run the Repo MCP Server (for git/fs tools)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.aichat.console.mcp.repo.RepoMcpServerMainKt")
    standardInput = System.`in`
    standardOutput = System.out
}

// Task for running the CRM MCP Server
tasks.register<JavaExec>("runCrmMcpServer") {
    group = "application"
    description = "Run the CRM MCP Server (for support tickets)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.aichat.console.mcp.crm.CrmMcpServerMainKt")
    standardInput = System.`in`
    standardOutput = System.out
}

// Task to create a fat JAR for CRM MCP Server
tasks.register<Jar>("crmMcpServerJar") {
    group = "build"
    description = "Create a fat JAR for CRM MCP Server"
    archiveBaseName.set("crm-mcp-server")
    archiveVersion.set("1.0.0")
    
    manifest {
        attributes(
            "Main-Class" to "com.example.aichat.console.mcp.crm.CrmMcpServerMainKt"
        )
    }
    
    // Include all dependencies
    from(sourceSets["main"].output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Task для запуска RAG примера
tasks.register<JavaExec>("runRag") {
    group = "application"
    description = "Run RAG pipeline example"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.aichat.console.rag.RAGExample")
    
    // Parse arguments properly, handling quotes
    args = project.findProperty("rag.args")?.toString()?.let { argString ->
        parseCommandLineArgs(argString)
    } ?: emptyList()
}

// Task for running AI PR Review
tasks.register<JavaExec>("runPrReview") {
    group = "application"
    description = "Run AI PR Review"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.aichat.console.prreview.AiPrReview")
    
    // Parse arguments from command line
    args = project.findProperty("prreview.args")?.toString()?.let { argString ->
        parseCommandLineArgs(argString)
    } ?: emptyList()
}

// Helper function to parse command line arguments with quotes
fun parseCommandLineArgs(argString: String): List<String> {
    val args = mutableListOf<String>()
    val currentArg = StringBuilder()
    var inQuotes = false
    var quoteChar: Char? = null
    
    var i = 0
    while (i < argString.length) {
        val char = argString[i]
        when {
            (char == '"' || char == '\'') -> {
                if (inQuotes && char == quoteChar) {
                    // Closing quote
                    inQuotes = false
                    quoteChar = null
                } else if (!inQuotes) {
                    // Opening quote
                    inQuotes = true
                    quoteChar = char
                } else {
                    // Different quote type inside string - add as-is
                    currentArg.append(char)
                }
            }
            char == ' ' && !inQuotes -> {
                if (currentArg.isNotEmpty()) {
                    args.add(currentArg.toString())
                    currentArg.clear()
                }
            }
            else -> {
                currentArg.append(char)
            }
        }
        i++
    }
    
    if (currentArg.isNotEmpty()) {
        args.add(currentArg.toString())
    }
    
    return args
}
