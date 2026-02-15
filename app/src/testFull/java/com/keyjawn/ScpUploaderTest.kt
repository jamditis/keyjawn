package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class ScpUploaderTest {

    @Test
    fun `UploadResult success fields work`() {
        val result = ScpUploader.UploadResult(
            success = true,
            remotePath = "/tmp/keyjawn/img-20260215-120000.png"
        )
        assertTrue(result.success)
        assertEquals("/tmp/keyjawn/img-20260215-120000.png", result.remotePath)
        assertEquals("", result.error)
    }

    @Test
    fun `UploadResult failure fields work`() {
        val result = ScpUploader.UploadResult(
            success = false,
            error = "Connection refused"
        )
        assertFalse(result.success)
        assertEquals("", result.remotePath)
        assertEquals("Connection refused", result.error)
    }

    @Test
    fun `UploadResult defaults are correct`() {
        val result = ScpUploader.UploadResult(success = false)
        assertEquals("", result.remotePath)
        assertEquals("", result.error)
    }

    @Test
    fun `uploader can be instantiated`() {
        val uploader = ScpUploader()
        assertNotNull(uploader)
    }

    @Test
    fun `upload with unreachable host returns failure`() {
        val uploader = ScpUploader()
        val host = HostConfig(
            name = "bad",
            hostname = "192.0.2.1",
            username = "nobody"
        )
        val fakeFile = java.io.File("/nonexistent/file.png")
        val result = uploader.upload(host, fakeFile)
        assertFalse(result.success)
        assertTrue(result.error.isNotBlank())
    }
}
