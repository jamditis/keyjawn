package com.keyjawn

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

class SettingsActivity : Activity() {

    private var hostStorage: HostStorage? = null
    private var hostListContainer: LinearLayout? = null
    private lateinit var commandSetsContainer: LinearLayout
    private var slashCommandRegistry: SlashCommandRegistry? = null
    private lateinit var billingManager: BillingManager

    private val hasScpUpload: Boolean
        get() = BuildConfig.FLAVOR == "full"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        billingManager = BillingManager(this)
        billingManager.connect()

        commandSetsContainer = findViewById(R.id.command_sets)

        setupUpgradeButton()

        val hostSection = findViewById<LinearLayout>(R.id.host_section)
        if (hasScpUpload) {
            hostStorage = HostStorage(this)
            hostListContainer = findViewById(R.id.host_list)
            findViewById<Button>(R.id.add_host_btn).setOnClickListener {
                showAddHostDialog()
            }
            refreshHostList()
        } else {
            hostSection.visibility = View.GONE
        }

        try {
            slashCommandRegistry = SlashCommandRegistry(this)
        } catch (_: Exception) {
            // commands.json missing or malformed
        }

        val flavorLabel = if (hasScpUpload) "" else " lite"
        findViewById<TextView>(R.id.version_text).text =
            "KeyJawn$flavorLabel v${BuildConfig.VERSION_NAME}"

        refreshCommandSets()
    }

    private fun refreshHostList() {
        val storage = hostStorage ?: return
        val container = hostListContainer ?: return
        container.removeAllViews()
        val hosts = storage.getHosts()
        val activeIndex = storage.getActiveHostIndex()

        hosts.forEachIndexed { index, host ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.host_item, container, false)

            val radio = itemView.findViewById<RadioButton>(R.id.host_radio)
            val nameText = itemView.findViewById<TextView>(R.id.host_name)
            val detailText = itemView.findViewById<TextView>(R.id.host_detail)
            val deleteBtn = itemView.findViewById<ImageButton>(R.id.host_delete)

            nameText.text = host.displayName()
            detailText.text = "${host.username}@${host.hostname}:${host.port}"
            radio.isChecked = index == activeIndex

            radio.setOnClickListener {
                storage.setActiveHostIndex(index)
                refreshHostList()
            }

            deleteBtn.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove host")
                    .setMessage("Remove ${host.displayName()}?")
                    .setPositiveButton("Remove") { _, _ ->
                        storage.removeHost(index)
                        val newActive = storage.getActiveHostIndex()
                        if (newActive >= storage.getHosts().size) {
                            storage.setActiveHostIndex(0)
                        }
                        refreshHostList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            container.addView(itemView)
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
                    hostStorage?.addHost(host)
                    refreshHostList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }

    private fun setupUpgradeButton() {
        val upgradeBtn = findViewById<Button>(R.id.upgrade_btn)
        if (billingManager.isFullVersion) {
            upgradeBtn.visibility = View.GONE
        } else {
            upgradeBtn.visibility = View.VISIBLE
            upgradeBtn.setOnClickListener {
                billingManager.launchPurchaseFlow(this)
            }
            billingManager.onPurchaseStateChanged = {
                runOnUiThread {
                    if (billingManager.isFullVersion) {
                        upgradeBtn.visibility = View.GONE
                    }
                }
            }
        }
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
