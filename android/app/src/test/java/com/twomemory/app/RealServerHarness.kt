package com.twomemory.app

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Boots the REAL Spring Boot executable jar against a REAL throwaway PostgreSQL
 * database, the way the two-device gates demand: no fake server, no in-memory
 * store. Each gate gets its own database, port and storage directory so two test
 * classes can never read each other's rows.
 *
 * Requires the jar: `mvn -DskipTests package` in `server/`.
 */
object RealServerHarness {

    class Running(val port: Int, val process: Process, val baseUrl: String, val dbName: String)

    fun start(port: Int, dbName: String, bootstrapSecret: String, logName: String): Running {
        val adminUrl = envOrDefault("TEST_DB_ADMIN_URL", "jdbc:postgresql://localhost:5432/postgres")
        val dbUser = envOrDefault("TEST_DB_USER", "moon_letter")
        val dbPassword = requiredEnv("TEST_DB_PASSWORD")
        // Robolectric uses separate classloaders for native and legacy graphics.
        // Do not depend on DriverManager's process-global registration from a prior sandbox.
        val driver: java.sql.Driver = org.postgresql.Driver()
        requireNotNull(driver.connect(adminUrl, java.util.Properties().apply {
            setProperty("user", dbUser)
            setProperty("password", dbPassword)
        })).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP DATABASE IF EXISTS $dbName WITH (FORCE)")
                statement.execute("CREATE DATABASE $dbName")
            }
        }
        val serverDir = File("../../server/target")
        val jar = System.getenv("MOONLETTER_TEST_SERVER_JAR")?.let(::File) ?: serverDir.listFiles { file ->
            file.name.endsWith(".jar") && !file.name.contains("original")
        }?.firstOrNull() ?: error("server jar missing; run `mvn -DskipTests package` in server/ first")
        val java = (System.getenv("JAVA_HOME") ?: error("JAVA_HOME not set")) + "\\bin\\java.exe"
        val baseUrl = "http://127.0.0.1:$port"
        val storageDir = File(serverDir, "storage-$dbName")
        storageDir.mkdirs()
        val processBuilder = ProcessBuilder(
            java, "-jar", jar.absolutePath,
            "--server.port=$port",
            // The Windows/JDK setup used by the real-server gates cannot create
            // Tomcat's default NIO loopback pipe; NIO2 keeps the test real while
            // avoiding that host-specific selector failure.
            "--moon-letter.server.tomcat.nio2-connector=true",
            "--moon-letter.storage.local-dir=$storageDir",
        )
        processBuilder.environment()["JDBC_DATABASE_URL"] = "jdbc:postgresql://localhost:5432/$dbName"
        processBuilder.environment()["JDBC_DATABASE_USERNAME"] = dbUser
        processBuilder.environment()["JDBC_DATABASE_PASSWORD"] = dbPassword
        processBuilder.environment()["BOOTSTRAP_SECRET"] = bootstrapSecret
        val process = processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(File(serverDir, logName)))
            .redirectErrorStream(true)
            .start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()
        var up = false
        for (attempt in 0 until 60) {
            runCatching {
                client.newCall(Request.Builder().url("$baseUrl/actuator/health").build()).execute()
            }.getOrNull()?.use { response ->
                if (response.isSuccessful) up = true
            }
            if (up) break
            Thread.sleep(1000)
        }
        check(up) { "real server did not become healthy; see server/target/$logName" }
        return Running(port, process, baseUrl, dbName)
    }

    private fun envOrDefault(name: String, defaultValue: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaultValue

    private fun requiredEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("$name must be set to run the real-server Android gates")

    fun stop(running: Running) {
        running.process.destroy()
        running.process.waitFor()
    }
}
