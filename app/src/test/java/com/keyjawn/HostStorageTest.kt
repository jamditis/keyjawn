package com.keyjawn

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HostStorageTest {

    private lateinit var storage: HostStorage

    @Before
    fun setUp() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_hosts", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        storage = HostStorage.createForTest(prefs)
    }

    @Test
    fun `empty initial state returns empty list`() {
        assertEquals(emptyList<HostConfig>(), storage.getHosts())
    }

    @Test
    fun `addHost stores and retrieves`() {
        val host = HostConfig(name = "dev", hostname = "10.0.0.1", username = "pi")
        storage.addHost(host)

        val hosts = storage.getHosts()
        assertEquals(1, hosts.size)
        assertEquals("dev", hosts[0].name)
        assertEquals("10.0.0.1", hosts[0].hostname)
        assertEquals("pi", hosts[0].username)
        assertEquals(22, hosts[0].port)
    }

    @Test
    fun `multiple hosts stored independently`() {
        storage.addHost(HostConfig(name = "dev", hostname = "10.0.0.1", username = "pi"))
        storage.addHost(HostConfig(name = "prod", hostname = "10.0.0.2", username = "admin"))

        val hosts = storage.getHosts()
        assertEquals(2, hosts.size)
        assertEquals("dev", hosts[0].name)
        assertEquals("prod", hosts[1].name)
    }

    @Test
    fun `removeHost by index`() {
        storage.addHost(HostConfig(name = "first", hostname = "10.0.0.1", username = "a"))
        storage.addHost(HostConfig(name = "second", hostname = "10.0.0.2", username = "b"))
        storage.addHost(HostConfig(name = "third", hostname = "10.0.0.3", username = "c"))

        storage.removeHost(1)

        val hosts = storage.getHosts()
        assertEquals(2, hosts.size)
        assertEquals("first", hosts[0].name)
        assertEquals("third", hosts[1].name)
    }

    @Test
    fun `removeHost with out-of-bounds index does nothing`() {
        storage.addHost(HostConfig(name = "only", hostname = "10.0.0.1", username = "pi"))

        storage.removeHost(5)
        storage.removeHost(-1)

        assertEquals(1, storage.getHosts().size)
    }

    @Test
    fun `active host index defaults to 0`() {
        assertEquals(0, storage.getActiveHostIndex())
    }

    @Test
    fun `setActiveHostIndex persists`() {
        storage.setActiveHostIndex(2)
        assertEquals(2, storage.getActiveHostIndex())
    }

    @Test
    fun `getActiveHost returns correct host`() {
        storage.addHost(HostConfig(name = "first", hostname = "10.0.0.1", username = "a"))
        storage.addHost(HostConfig(name = "second", hostname = "10.0.0.2", username = "b"))
        storage.setActiveHostIndex(1)

        val active = storage.getActiveHost()
        assertNotNull(active)
        assertEquals("second", active!!.name)
    }

    @Test
    fun `getActiveHost returns null when no hosts`() {
        assertNull(storage.getActiveHost())
    }

    @Test
    fun `getActiveHost returns null when index out of range`() {
        storage.addHost(HostConfig(name = "only", hostname = "10.0.0.1", username = "pi"))
        storage.setActiveHostIndex(5)

        assertNull(storage.getActiveHost())
    }

    @Test
    fun `saveHosts overwrites previous list`() {
        storage.addHost(HostConfig(name = "old", hostname = "10.0.0.1", username = "a"))
        storage.addHost(HostConfig(name = "also old", hostname = "10.0.0.2", username = "b"))

        storage.saveHosts(listOf(
            HostConfig(name = "new", hostname = "10.0.0.99", username = "z")
        ))

        val hosts = storage.getHosts()
        assertEquals(1, hosts.size)
        assertEquals("new", hosts[0].name)
    }

    @Test
    fun `custom port and uploadDir round-trip through storage`() {
        storage.addHost(HostConfig(
            name = "custom",
            hostname = "10.0.0.1",
            port = 2222,
            username = "pi",
            uploadDir = "/home/pi/img/"
        ))

        val host = storage.getHosts()[0]
        assertEquals(2222, host.port)
        assertEquals("/home/pi/img/", host.uploadDir)
    }

    @Test
    fun `privateKeyPath round-trips through storage`() {
        storage.addHost(HostConfig(
            name = "keyed",
            hostname = "10.0.0.1",
            username = "pi",
            privateKeyPath = "/data/keys/id_ed25519"
        ))

        val host = storage.getHosts()[0]
        assertEquals("/data/keys/id_ed25519", host.privateKeyPath)
    }

    @Test
    fun `null privateKeyPath stored as empty string and read back`() {
        storage.addHost(HostConfig(
            name = "nokey",
            hostname = "10.0.0.1",
            username = "pi",
            privateKeyPath = null
        ))

        val host = storage.getHosts()[0]
        // optString with null default returns "" for stored empty string
        // so the round-trip converts null -> "" -> read back as ""
        // This is expected behavior for JSON serialization
        assertTrue(host.privateKeyPath == null || host.privateKeyPath == "")
    }
}
