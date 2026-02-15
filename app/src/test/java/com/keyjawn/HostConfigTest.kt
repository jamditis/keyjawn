package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class HostConfigTest {

    @Test
    fun `isValid returns true for valid config`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", username = "pi")
        assertTrue(config.isValid())
    }

    @Test
    fun `isValid returns false for empty hostname`() {
        val config = HostConfig(name = "dev", hostname = "", username = "pi")
        assertFalse(config.isValid())
    }

    @Test
    fun `isValid returns false for blank hostname`() {
        val config = HostConfig(name = "dev", hostname = "   ", username = "pi")
        assertFalse(config.isValid())
    }

    @Test
    fun `isValid returns false for empty username`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", username = "")
        assertFalse(config.isValid())
    }

    @Test
    fun `isValid returns false for blank username`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", username = "   ")
        assertFalse(config.isValid())
    }

    @Test
    fun `displayName uses name when set`() {
        val config = HostConfig(name = "my server", hostname = "10.0.0.1", username = "pi")
        assertEquals("my server", config.displayName())
    }

    @Test
    fun `displayName falls back to user at host`() {
        val config = HostConfig(name = "", hostname = "10.0.0.1", username = "pi")
        assertEquals("pi@10.0.0.1", config.displayName())
    }

    @Test
    fun `displayName falls back when name is blank`() {
        val config = HostConfig(name = "   ", hostname = "10.0.0.1", username = "pi")
        assertEquals("pi@10.0.0.1", config.displayName())
    }

    @Test
    fun `default port is 22`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", username = "pi")
        assertEquals(22, config.port)
    }

    @Test
    fun `custom port is preserved`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", port = 2222, username = "pi")
        assertEquals(2222, config.port)
    }

    @Test
    fun `default uploadDir is tmp keyjawn`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", username = "pi")
        assertEquals("/tmp/keyjawn/", config.uploadDir)
    }

    @Test
    fun `custom uploadDir is preserved`() {
        val config = HostConfig(
            name = "dev", hostname = "10.0.0.1", username = "pi",
            uploadDir = "/home/pi/uploads/"
        )
        assertEquals("/home/pi/uploads/", config.uploadDir)
    }

    @Test
    fun `privateKeyPath defaults to null`() {
        val config = HostConfig(name = "dev", hostname = "10.0.0.1", username = "pi")
        assertNull(config.privateKeyPath)
    }

    @Test
    fun `privateKeyPath can be set`() {
        val config = HostConfig(
            name = "dev", hostname = "10.0.0.1", username = "pi",
            privateKeyPath = "/data/keys/id_ed25519"
        )
        assertEquals("/data/keys/id_ed25519", config.privateKeyPath)
    }
}
