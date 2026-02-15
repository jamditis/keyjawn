package com.keyjawn

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
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
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)

            if (knownHostsManager != null) {
                val hostKey = session.hostKey
                if (hostKey != null && !knownHostsManager.checkAndStore(host.hostname, host.port, hostKey, jsch)) {
                    session.disconnect()
                    return UploadResult(
                        success = false,
                        error = "Host key changed for ${host.hostname}:${host.port}. Possible MITM attack. Clear the stored key in settings to accept the new one."
                    )
                }
            }

            val mkdirChannel = session.openChannel("exec") as ChannelExec
            mkdirChannel.setCommand("mkdir -p ${host.uploadDir}")
            mkdirChannel.connect()
            mkdirChannel.disconnect()

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val extension = localFile.extension.ifEmpty { "png" }
            val remoteFileName = "img-$timestamp.$extension"
            val remotePath = "${host.uploadDir}$remoteFileName"

            val scpCommand = "scp -t $remotePath"
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
