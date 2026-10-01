package com.twomemory.app

import java.io.File
import java.sql.DriverManager
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

    private const val ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres"
    private const val DB_USER = "moon_letter"
    private const val DB_PASSWORD = "moon_letter_dev_only"

    class Running(val port: Int, val process: Process, val baseUrl: String, val dbName: String)

    fun start(port: Int, dbName: String, bootstrapSecret: String, logName: String): Running {
        DriverManager.getConnection(ADMIN_URL, DB_USER, DB_PASSWORD).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP DATABASE IF EXISTS $dbName WITH (FORCE)")
                statement.execute("CREATE DATABASE $dbName")
            }
        }
        val serverDir = File("../../server/target")
        val jar = serverDir.listFiles { file ->
            file.name.endsWith(".jar") && !file.name.contains("original")
        }?.firstOrNull() ?: error("server jar missing; run `mvn -DskipTests package` in server/ first")
        val java = (System.getenv("JAVA_HOME") ?: error("JAVA_HOME not set")) + "\\bin\\java.exe"
        val baseUrl = "http://127.0.0.1:$port"
        val storageDir = File(serverDir, "storage-$dbName")
        storageDir.mkdirs()
        val process = ProcessBuilder(
            java, "-jar", jar.absolutePath,
            "--server.port=$port",
            "--spring.datasource.url=jdbc:postgresql://localhost:5432/$dbName",
            "--spring.datasource.username=$DB_USER",
            "--spring.datasource.password=$DB_PASSWORD",
            "--moon-letter.bootstrap.secret=$bootstrapSecret",
            "--moon-letter.storage.local-dir=$storageDir",
        ).redirectOutput(ProcessBuilder.Redirect.appendTo(File(serverDir, logName)))
            .redirectErrorStream(true)
            .start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()
        var up = false
        repeat(60) {
            runCatching {
                client.newCall(Request.Builder().url("$baseUrl/actuator/health").build()).execute()
            }.getOrNull()?.use { response ->
                if (response.isSuccessful) up = true
            }
            if (!up) Thread.sleep(1000)
        }
        check(up) { "real server did not become healthy; see server/target/$logName" }
        return Running(port, process, baseUrl, dbName)
    }

    fun stop(running: Running) {
        running.process.destroy()
        running.process.waitFor()
    }
}
