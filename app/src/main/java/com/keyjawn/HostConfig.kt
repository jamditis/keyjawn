package com.keyjawn

data class HostConfig(
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val privateKeyPath: String? = null,
    val uploadDir: String = "/tmp/keyjawn/"
) {
    fun isValid(): Boolean {
        return hostname.isNotBlank() && username.isNotBlank()
    }

    fun displayName(): String {
        return if (name.isNotBlank()) name else "$username@$hostname"
    }
}
