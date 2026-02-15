package com.keyjawn

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScpUploader {

    data class UploadResult(
        val success: Boolean,
        val remotePath: String = "",
        val error: String = ""
    )

    fun upload(host: HostConfig, localFile: File, knownHostsManager: KnownHostsManager? = null): UploadResult {
        val jsch = JSch()

        if (!host.privateKeyPath.isNullOrBlank()) {
            jsch.addIdentity(host.privateKeyPath)
        }

        var session: Session? = null
        try {
            session = jsch.getSession(host.username, host.hostname, host.port)

            if (!host.password.isNullOrBlank()) {
                session.setPassword(host.password)
            }

            if (knownHostsManager != null) {
                // Use StrictHostKeyChecking=ask so JSch calls UserInfo for unknown hosts.
                // Our TofuUserInfo accepts new keys (TOFU) but rejects changed keys.
                session.setConfig("StrictHostKeyChecking", "ask")
                session.userInfo = TofuUserInfo(knownHostsManager, host, jsch)
            } else {
                session.setConfig("StrictHostKeyChecking", "no")
            }

            session.connect(10000)

            // After successful connect, store the key if it was new (TOFU)
            if (knownHostsManager != null) {
                val hostKey = session.hostKey
                if (hostKey != null) {
                    val status = knownHostsManager.check(host.hostname, host.port, hostKey, jsch)
                    if (status == KnownHostsManager.KeyStatus.NEW) {
                        knownHostsManager.store(host.hostname, host.port, hostKey, jsch)
                    }
                }
            }

            // Use SFTP for mkdir (avoids shell injection)
            val uploadDir = host.uploadDir.let {
                if (it.endsWith("/")) it else "$it/"
            }
            try {
                val sftpChannel = session.openChannel("sftp") as ChannelSftp
                sftpChannel.connect()
                mkdirRecursive(sftpChannel, uploadDir)
                sftpChannel.disconnect()
            } catch (e: Exception) {
                // Directory may already exist, continue with upload
            }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val extension = localFile.extension.ifEmpty { "png" }
            val remoteFileName = "img-$timestamp.$extension"
            val remotePath = "$uploadDir$remoteFileName"

            // Shell-escape the remote path for SCP command
            val escapedPath = shellEscape(remotePath)
            val scpCommand = "scp -t $escapedPath"
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(scpCommand)
            val outputStream = channel.outputStream
            val inputStream = channel.inputStream
            channel.connect()

            checkAck(inputStream)

            val fileSize = localFile.length()
            val header = "C0644 $fileSize $remoteFileName\n"
            outputStream.write(header.toByteArray())
            outputStream.flush()
            checkAck(inputStream)

            FileInputStream(localFile).use { fis ->
                val buf = ByteArray(4096)
                var len: Int
                while (fis.read(buf).also { len = it } > 0) {
                    outputStream.write(buf, 0, len)
                }
            }

            outputStream.write(0)
            outputStream.flush()
            checkAck(inputStream)

            channel.disconnect()

            return UploadResult(success = true, remotePath = remotePath)
        } catch (e: Exception) {
            return UploadResult(success = false, error = e.message ?: "Upload failed")
        } finally {
            session?.disconnect()
        }
    }

    /**
     * TOFU (trust on first use) UserInfo for JSch.
     * When StrictHostKeyChecking=ask, JSch calls promptYesNo() for unknown host keys.
     * We accept new keys but reject changed keys (mismatch = possible MITM).
     */
    private class TofuUserInfo(
        private val knownHostsManager: KnownHostsManager,
        private val host: HostConfig,
        private val jsch: JSch
    ) : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = host.password
        override fun promptPassword(message: String?): Boolean = !host.password.isNullOrBlank()
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean {
            // JSch asks this when the host key is unknown.
            // Check if we have a stored key that differs (MISMATCH = reject).
            // If no stored key, accept (TOFU).
            val stored = knownHostsManager.getStoredFingerprint(host.hostname, host.port)
            return stored == null // Accept only if we have no stored key (first use)
        }
        override fun showMessage(message: String?) {}
    }

    private fun mkdirRecursive(sftp: ChannelSftp, path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = if (path.startsWith("/")) "/" else ""
        for (part in parts) {
            current = if (current == "/") "/$part" else "$current/$part"
            try {
                sftp.stat(current)
            } catch (e: SftpException) {
                sftp.mkdir(current)
            }
        }
    }

    private fun shellEscape(s: String): String {
        return "'" + s.replace("'", "'\\''") + "'"
    }

    private fun checkAck(inputStream: java.io.InputStream) {
        val b = inputStream.read()
        if (b == 1 || b == 2) {
            val sb = StringBuilder()
            var c: Int
            while (inputStream.read().also { c = it } != '\n'.code) {
                sb.append(c.toChar())
            }
            throw RuntimeException("SCP error: $sb")
        }
    }
}
