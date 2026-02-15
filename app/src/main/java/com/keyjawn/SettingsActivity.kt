package com.keyjawn

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var hostStorage: HostStorage
    private lateinit var hostListContainer: LinearLayout
    private lateinit var commandSetsContainer: LinearLayout
    private var slashCommandRegistry: SlashCommandRegistry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        hostStorage = HostStorage(this)
        hostListContainer = findViewById(R.id.host_list)
        commandSetsContainer = findViewById(R.id.command_sets)

        try {
            slashCommandRegistry = SlashCommandRegistry(this)
        } catch (_: Exception) {
            // commands.json missing or malformed
        }

        findViewById<Button>(R.id.add_host_btn).setOnClickListener {
            showAddHostDialog()
        }

        findViewById<TextView>(R.id.version_text).text =
            "KeyJawn v${BuildConfig.VERSION_NAME}"

        refreshHostList()
        refreshCommandSets()
    }

    private fun refreshHostList() {
        hostListContainer.removeAllViews()
        val hosts = hostStorage.getHosts()
        val activeIndex = hostStorage.getActiveHostIndex()

        hosts.forEachIndexed { index, host ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.host_item, hostListContainer, false)

            val radio = itemView.findViewById<RadioButton>(R.id.host_radio)
            val nameText = itemView.findViewById<TextView>(R.id.host_name)
            val detailText = itemView.findViewById<TextView>(R.id.host_detail)
            val deleteBtn = itemView.findViewById<ImageButton>(R.id.host_delete)

            nameText.text = host.displayName()
            detailText.text = "${host.username}@${host.hostname}:${host.port}"
            radio.isChecked = index == activeIndex

            radio.setOnClickListener {
                hostStorage.setActiveHostIndex(index)
                refreshHostList()
            }

            deleteBtn.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove host")
                    .setMessage("Remove ${host.displayName()}?")
                    .setPositiveButton("Remove") { _, _ ->
                        hostStorage.removeHost(index)
                        val newActive = hostStorage.getActiveHostIndex()
                        if (newActive >= hostStorage.getHosts().size) {
                            hostStorage.setActiveHostIndex(0)
                        }
                        refreshHostList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            hostListContainer.addView(itemView)
        }
    }

    private fun showAddHostDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val nameField = EditText(this).apply { hint = "Name (optional)" }
        val hostnameField = EditText(this).apply { hint = "Hostname or IP" }
        val portField = EditText(this).apply {
            hint = "Port (default 22)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val usernameField = EditText(this).apply { hint = "Username" }
        val keyPathField = EditText(this).apply { hint = "Private key path (optional)" }
        val uploadDirField = EditText(this).apply {
            hint = "Upload dir (default /tmp/keyjawn/)"
        }

        dialogView.addView(nameField)
        dialogView.addView(hostnameField)
        dialogView.addView(portField)
        dialogView.addView(usernameField)
        dialogView.addView(keyPathField)
        dialogView.addView(uploadDirField)

        AlertDialog.Builder(this)
            .setTitle("Add SSH host")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val portText = portField.text.toString()
                val port = portText.toIntOrNull() ?: 22
                val uploadDir = uploadDirField.text.toString().ifBlank { "/tmp/keyjawn/" }
                val keyPath = keyPathField.text.toString().ifBlank { null }

                val host = HostConfig(
                    name = nameField.text.toString().trim(),
                    hostname = hostnameField.text.toString().trim(),
                    port = port,
                    username = usernameField.text.toString().trim(),
                    privateKeyPath = keyPath,
                    uploadDir = uploadDir
                )

                if (host.isValid()) {
                    hostStorage.addHost(host)
                    refreshHostList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshCommandSets() {
        commandSetsContainer.removeAllViews()
        val registry = slashCommandRegistry ?: return
        val sets = registry.getAvailableSets()
        val enabled = registry.getEnabledSets()

        sets.forEach { (id, commandSet) ->
            val checkbox = CheckBox(this).apply {
                text = "${commandSet.label} (${commandSet.commands.size})"
                setTextColor(0xFFDDDDDD.toInt())
                isChecked = id in enabled
                setOnCheckedChangeListener { _, checked ->
                    registry.setEnabled(id, checked)
                }
            }
            commandSetsContainer.addView(checkbox)
        }
    }
}
