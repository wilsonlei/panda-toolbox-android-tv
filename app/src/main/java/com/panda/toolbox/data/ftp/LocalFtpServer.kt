package com.panda.toolbox.data.ftp

import com.panda.toolbox.data.FileRepository
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LocalFtpServer(
    private val onStateChanged: (isRunning: Boolean, url: String?, activeConnections: Int) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private val activeConnections = AtomicInteger(0)
    private val clientSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var clientExecutor: ExecutorService? = null
    private var currentUrl: String? = null

    val isRunning: Boolean
        get() = running.get()

    fun start(rootDirectory: File, port: Int = DEFAULT_PORT): Result<String> {
        if (running.get()) {
            return Result.success(currentUrl ?: "")
        }

        val localIp = findLocalIpAddress()
            ?: return Result.failure(IllegalStateException("NO_NETWORK"))

        val rootCanonical = try {
            rootDirectory.canonicalFile
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            val socket = ServerSocket(port)
            serverSocket = socket
            val url = "ftp://$localIp:$port"
            currentUrl = url
            running.set(true)
            activeConnections.set(0)

            val acceptPool = Executors.newSingleThreadExecutor()
            val clientPool = Executors.newCachedThreadPool()
            acceptExecutor = acceptPool
            clientExecutor = clientPool

            notifyState()
            onLog("Server started at $url (root: ${rootCanonical.absolutePath})")

            acceptPool.execute {
                while (running.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        clientSockets.add(client)
                        clientPool.execute {
                            handleClient(client, rootCanonical, localIp)
                        }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }

            Result.success(url)
        } catch (e: Exception) {
            stop()
            Result.failure(e)
        }
    }

    fun stop() {
        val wasRunning = running.getAndSet(false)
        currentUrl = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        clientSockets.forEach { socket ->
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
        clientSockets.clear()

        acceptExecutor?.shutdownNow()
        acceptExecutor = null
        clientExecutor?.shutdownNow()
        clientExecutor = null

        activeConnections.set(0)
        notifyState()
        if (wasRunning) {
            onLog("Server stopped")
        }
    }

    private fun notifyState() {
        onStateChanged(running.get(), currentUrl, activeConnections.get())
    }

    private fun handleClient(clientSocket: Socket, rootCanonical: File, localIp: String) {
        val remoteIp = clientSocket.inetAddress?.hostAddress ?: "unknown"
        val count = activeConnections.incrementAndGet()
        notifyState()
        onLog("Connected: $remoteIp (active: $count)")

        var pasvServerSocket: ServerSocket? = null
        var activeDataHost: String? = null
        var activeDataPort: Int = -1
        var currentVirtualDir = "/"
        var restartOffset = 0L
        var renameFromFile: File? = null
        var authenticated = false

        try {
            val reader = BufferedReader(
                InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            )
            val writer = PrintWriter(
                OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8),
                true
            )

            fun send(response: String) {
                writer.print("$response\r\n")
                writer.flush()
            }

            fun closePassive() {
                try {
                    pasvServerSocket?.close()
                } catch (_: Exception) {
                }
                pasvServerSocket = null
            }

            fun openDataSocket(): Socket? {
                val pasv = pasvServerSocket
                if (pasv != null) {
                    return try {
                        pasv.soTimeout = DATA_TIMEOUT_MS
                        pasv.accept()
                    } catch (_: Exception) {
                        null
                    } finally {
                        closePassive()
                    }
                }
                val host = activeDataHost
                val port = activeDataPort
                if (host != null && port > 0) {
                    return try {
                        Socket(host, port).apply { soTimeout = DATA_TIMEOUT_MS }
                    } catch (_: Exception) {
                        null
                    }
                }
                return null
            }

            send("220 Panda Toolbox FTP Server ready")

            while (running.get() && !clientSocket.isClosed) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val spaceIndex = trimmed.indexOf(' ')
                val cmd = (if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex))
                    .uppercase(Locale.US)
                val arg = if (spaceIndex == -1) "" else trimmed.substring(spaceIndex + 1).trim()

                when (cmd) {
                    "USER" -> {
                        authenticated = true
                        send("230 Anonymous login ok")
                    }
                    "PASS" -> {
                        authenticated = true
                        send("230 User logged in")
                    }
                    "SYST" -> send("215 UNIX Type: L8")
                    "FEAT" -> {
                        writer.print("211-Features:\r\n")
                        writer.print(" UTF8\r\n")
                        writer.print(" SIZE\r\n")
                        writer.print(" MDTM\r\n")
                        writer.print(" PASV\r\n")
                        writer.print(" EPSV\r\n")
                        writer.print(" REST STREAM\r\n")
                        writer.print(" MLSD\r\n")
                        writer.print("211 End\r\n")
                        writer.flush()
                    }
                    "OPTS" -> send("200 OK")
                    "TYPE" -> send("200 Type set to $arg")
                    "MODE" -> send("200 Mode set to S")
                    "STRU" -> send("200 Structure set to F")
                    "NOOP" -> send("200 NOOP ok")
                    "QUIT" -> {
                        send("221 Goodbye")
                        break
                    }
                    "ABOR" -> {
                        closePassive()
                        send("226 Abort successful")
                    }
                    "PWD", "XPWD" -> {
                        if (!authenticated) {
                            send("530 Please login with USER and PASS")
                        } else {
                            send("257 \"$currentVirtualDir\" is current directory")
                        }
                    }
                    "CWD", "XCWD" -> {
                        val (nextVirtual, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && targetFile.exists() && targetFile.isDirectory) {
                            currentVirtualDir = nextVirtual
                            send("250 Directory changed to $currentVirtualDir")
                        } else {
                            send("550 Directory not found")
                        }
                    }
                    "CDUP", "XCUP" -> {
                        val (nextVirtual, targetFile) = resolvePath(rootCanonical, currentVirtualDir, "..")
                        if (targetFile != null && targetFile.exists() && targetFile.isDirectory) {
                            currentVirtualDir = nextVirtual
                            send("250 Directory changed to $currentVirtualDir")
                        } else {
                            send("550 Failed to change directory")
                        }
                    }
                    "PASV" -> {
                        closePassive()
                        try {
                            val dataServer = ServerSocket(0, 1, InetAddress.getByName(localIp))
                            pasvServerSocket = dataServer
                            val p = dataServer.localPort
                            val ipParts = localIp.split(".")
                            if (ipParts.size == 4) {
                                send("227 Entering Passive Mode (${ipParts[0]},${ipParts[1]},${ipParts[2]},${ipParts[3]},${p / 256},${p % 256})")
                            } else {
                                send("425 Unable to enter passive mode")
                            }
                        } catch (_: Exception) {
                            send("425 Unable to open passive connection")
                        }
                    }
                    "EPSV" -> {
                        closePassive()
                        try {
                            val dataServer = ServerSocket(0)
                            pasvServerSocket = dataServer
                            send("229 Entering Extended Passive Mode (|||${dataServer.localPort}|)")
                        } catch (_: Exception) {
                            send("425 Unable to open passive connection")
                        }
                    }
                    "PORT" -> {
                        closePassive()
                        val parts = arg.split(",")
                        if (parts.size == 6) {
                            val p1 = parts[4].toIntOrNull()
                            val p2 = parts[5].toIntOrNull()
                            if (p1 != null && p2 != null) {
                                activeDataHost = "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"
                                activeDataPort = p1 * 256 + p2
                                send("200 PORT command successful")
                            } else {
                                send("501 Syntax error in parameters")
                            }
                        } else {
                            send("501 Syntax error in parameters")
                        }
                    }
                    "REST" -> {
                        val offset = arg.toLongOrNull()
                        if (offset != null && offset >= 0L) {
                            restartOffset = offset
                            send("350 Restarting at $offset")
                        } else {
                            send("501 Invalid REST offset")
                        }
                    }
                    "SIZE" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                            send("213 ${targetFile.length()}")
                        } else {
                            send("550 File not found")
                        }
                    }
                    "MDTM" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && targetFile.exists()) {
                            send("213 ${formatMdtm(targetFile.lastModified())}")
                        } else {
                            send("550 File not found")
                        }
                    }
                    "LIST", "NLST", "MLSD" -> {
                        val listArg = stripListFlags(arg)
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, listArg)
                        if (targetFile == null || !targetFile.exists()) {
                            send("550 Path not found")
                            continue
                        }
                        send("150 Opening data connection for directory list")
                        val dataSocket = openDataSocket()
                        if (dataSocket == null) {
                            send("425 Can't open data connection")
                            continue
                        }
                        try {
                            val entries = if (targetFile.isDirectory) {
                                targetFile.listFiles()?.sortedWith(
                                    compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                                ) ?: emptyList()
                            } else {
                                listOf(targetFile)
                            }
                            dataSocket.getOutputStream().use { out ->
                                val dataWriter = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8), false)
                                for (entry in entries) {
                                    when (cmd) {
                                        "NLST" -> dataWriter.print("${entry.name}\r\n")
                                        "MLSD" -> dataWriter.print("${formatMlsdEntry(entry)}\r\n")
                                        else -> dataWriter.print("${formatUnixListEntry(entry)}\r\n")
                                    }
                                }
                                dataWriter.flush()
                            }
                            send("226 Transfer complete")
                        } catch (_: Exception) {
                            send("426 Connection closed; transfer aborted")
                        } finally {
                            try {
                                dataSocket.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    "RETR" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile == null || !targetFile.exists() || !targetFile.isFile) {
                            restartOffset = 0L
                            send("550 File not found")
                            continue
                        }
                        send("150 Opening binary mode data connection for ${targetFile.name}")
                        val dataSocket = openDataSocket()
                        if (dataSocket == null) {
                            restartOffset = 0L
                            send("425 Can't open data connection")
                            continue
                        }
                        val offset = restartOffset
                        restartOffset = 0L
                        try {
                            RandomAccessFile(targetFile, "r").use { raf ->
                                if (offset > 0L) {
                                    raf.seek(offset)
                                }
                                BufferedOutputStream(dataSocket.getOutputStream(), BUFFER_SIZE).use { out ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    while (true) {
                                        val read = raf.read(buffer)
                                        if (read <= 0) break
                                        out.write(buffer, 0, read)
                                    }
                                    out.flush()
                                }
                            }
                            onLog("Downloaded: ${targetFile.name} (${FileRepository.formatSize(targetFile.length())})")
                            send("226 Transfer complete")
                        } catch (_: Exception) {
                            send("426 Connection closed; transfer aborted")
                        } finally {
                            try {
                                dataSocket.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    "STOR", "APPE" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile == null || targetFile.isDirectory) {
                            restartOffset = 0L
                            send("550 Invalid file path")
                            continue
                        }
                        targetFile.parentFile?.mkdirs()
                        send("150 Opening binary mode data connection for ${targetFile.name}")
                        val dataSocket = openDataSocket()
                        if (dataSocket == null) {
                            restartOffset = 0L
                            send("425 Can't open data connection")
                            continue
                        }
                        val append = cmd == "APPE"
                        val offset = restartOffset
                        restartOffset = 0L
                        try {
                            var bytesWritten = 0L
                            BufferedInputStream(dataSocket.getInputStream(), BUFFER_SIZE).use { input ->
                                if (offset > 0L && !append) {
                                    RandomAccessFile(targetFile, "rw").use { raf ->
                                        raf.seek(offset)
                                        val buffer = ByteArray(BUFFER_SIZE)
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read <= 0) break
                                            raf.write(buffer, 0, read)
                                            bytesWritten += read
                                        }
                                    }
                                } else {
                                    FileOutputStream(targetFile, append).use { fos ->
                                        val buffer = ByteArray(BUFFER_SIZE)
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read <= 0) break
                                            fos.write(buffer, 0, read)
                                            bytesWritten += read
                                        }
                                        fos.flush()
                                    }
                                }
                            }
                            onLog("Uploaded: ${targetFile.name} (${FileRepository.formatSize(bytesWritten)})")
                            send("226 Transfer complete")
                        } catch (e: Exception) {
                            onLog("Upload failed: ${targetFile.name} (${e.message ?: "error"})")
                            send("550 Failed to write file")
                        } finally {
                            try {
                                dataSocket.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    "DELE" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && targetFile.exists() && targetFile.isFile && targetFile.delete()) {
                            onLog("Deleted: ${targetFile.name}")
                            send("250 File deleted")
                        } else {
                            send("550 Failed to delete file")
                        }
                    }
                    "MKD", "XMKD" -> {
                        val (virtualPath, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && !targetFile.exists() && targetFile.mkdirs()) {
                            onLog("Created folder: ${targetFile.name}")
                            send("257 \"$virtualPath\" created")
                        } else {
                            send("550 Failed to create directory")
                        }
                    }
                    "RMD", "XRMD" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && targetFile != rootCanonical && targetFile.exists() && targetFile.isDirectory && targetFile.deleteRecursively()) {
                            onLog("Removed folder: ${targetFile.name}")
                            send("250 Directory deleted")
                        } else {
                            send("550 Failed to remove directory")
                        }
                    }
                    "RNFR" -> {
                        val (_, targetFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (targetFile != null && targetFile.exists() && targetFile != rootCanonical) {
                            renameFromFile = targetFile
                            send("350 Ready for RNTO")
                        } else {
                            renameFromFile = null
                            send("550 File or directory not found")
                        }
                    }
                    "RNTO" -> {
                        val source = renameFromFile
                        renameFromFile = null
                        val (_, destFile) = resolvePath(rootCanonical, currentVirtualDir, arg)
                        if (source != null && destFile != null && source.renameTo(destFile)) {
                            onLog("Renamed: ${source.name} -> ${destFile.name}")
                            send("250 Rename successful")
                        } else {
                            send("550 Rename failed")
                        }
                    }
                    else -> send("502 Command $cmd not implemented")
                }
            }
        } catch (_: Exception) {
        } finally {
            closeQuietly(pasvServerSocket)
            closeQuietly(clientSocket)
            clientSockets.remove(clientSocket)
            val remaining = activeConnections.decrementAndGet().coerceAtLeast(0)
            activeConnections.set(remaining)
            notifyState()
            onLog("Disconnected: $remoteIp (active: $remaining)")
        }
    }

    private fun resolvePath(
        rootCanonical: File,
        currentVirtualDir: String,
        rawArg: String
    ): Pair<String, File?> {
        val combined = when {
            rawArg.isEmpty() -> currentVirtualDir
            rawArg.startsWith("/") -> rawArg
            currentVirtualDir == "/" -> "/$rawArg"
            else -> "$currentVirtualDir/$rawArg"
        }

        val segments = ArrayList<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }

        val normalizedVirtual = if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
        val candidate = if (segments.isEmpty()) {
            rootCanonical
        } else {
            File(rootCanonical, segments.joinToString(File.separator))
        }

        return try {
            val canonical = candidate.canonicalFile
            val rootPath = rootCanonical.path
            val isInsideRoot = canonical.path == rootPath ||
                canonical.path.startsWith(rootPath + File.separator)
            if (isInsideRoot) normalizedVirtual to canonical else normalizedVirtual to null
        } catch (_: Exception) {
            normalizedVirtual to null
        }
    }

    private fun stripListFlags(arg: String): String {
        if (arg.isEmpty()) return ""
        val parts = arg.split(' ').filter { it.isNotBlank() }
        val nonFlag = parts.dropWhile { it.startsWith("-") }
        return nonFlag.joinToString(" ")
    }

    private fun formatUnixListEntry(file: File): String {
        val perms = if (file.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
        val size = if (file.isDirectory) 0L else file.length()
        val lastModified = file.lastModified()
        val sixMonthsAgo = System.currentTimeMillis() - SIX_MONTHS_MS
        val datePattern = if (lastModified < sixMonthsAgo) "MMM dd  yyyy" else "MMM dd HH:mm"
        val dateFormat = SimpleDateFormat(datePattern, Locale.US)
        val dateStr = dateFormat.format(Date(lastModified))
        return String.format(Locale.US, "%s 1 owner group %12d %s %s", perms, size, dateStr, file.name)
    }

    private fun formatMlsdEntry(file: File): String {
        val type = if (file.isDirectory) "dir" else "file"
        val size = if (file.isDirectory) 0L else file.length()
        val modify = formatMdtm(file.lastModified())
        return "type=$type;size=$size;modify=$modify; ${file.name}"
    }

    private fun formatMdtm(millis: Long): String {
        val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(millis))
    }

    private fun findLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var fallback: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        if (addr.isSiteLocalAddress) {
                            return addr.hostAddress
                        }
                        if (fallback == null) {
                            fallback = addr.hostAddress
                        }
                    }
                }
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    private fun closeQuietly(socket: ServerSocket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val DEFAULT_PORT = 2121
        private const val BUFFER_SIZE = 64 * 1024
        private const val DATA_TIMEOUT_MS = 15_000
        private const val SIX_MONTHS_MS = 180L * 24L * 60L * 60L * 1000L
    }
}
