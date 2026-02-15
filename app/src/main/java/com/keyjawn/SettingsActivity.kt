package com.keyjawn

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private var hostStorage: HostStorage? = null
    private var hostListContainer: LinearLayout? = null
    private lateinit var commandSetsContainer: LinearLayout
    private var slashCommandRegistry: SlashCommandRegistry? = null
    private lateinit var billingManager: BillingManager
    private lateinit var themeManager: ThemeManager

    private val hasScpUpload: Boolean
        get() = BuildConfig.FLAVOR == "full"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        billingManager = BillingManager(this)
        billingManager.connect()
        themeManager = ThemeManager(this)

        commandSetsContainer = findViewById(R.id.command_sets)

        setupUpgradeButton()
        setupHapticToggle()
        setupThemePicker()

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

        setupCustomCommandsButton()

        val flavorLabel = if (hasScpUpload) "" else " lite"
        findViewById<TextView>(R.id.version_text).text =
            "KeyJawn$flavorLabel v${BuildConfig.VERSION_NAME}"

        refreshCommandSets()
    }

    // -- Haptic toggle --

    private fun setupHapticToggle() {
        val appPrefs = AppPrefs(this)
        val toggle = findViewById<CheckBox>(R.id.haptic_toggle)
        toggle.isChecked = appPrefs.isHapticEnabled()
        toggle.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.setHapticEnabled(isChecked)
        }
    }

    // -- Theme picker --

    private fun setupThemePicker() {
        val themeSection = findViewById<LinearLayout>(R.id.theme_section)
        if (!billingManager.isFullVersion) {
            themeSection.visibility = View.GONE
            return
        }
        themeSection.visibility = View.VISIBLE

        val picker = findViewById<LinearLayout>(R.id.theme_picker)
        picker.removeAllViews()

        val density = resources.displayMetrics.density
        val selected = themeManager.currentTheme

        for (theme in themeManager.getAvailableThemes()) {
            val tm = ThemeManager(this)
            tm.currentTheme = theme
            val bgColor = tm.keyboardBg()
            val keyColor = tm.keyBg()
            val textColor = tm.keyText()

            val swatch = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val size = (64 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(0, size, 1f).apply {
                    val margin = (4 * density).toInt()
                    setMargins(margin, 0, margin, 0)
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(bgColor)
                    if (theme == selected) {
                        setStroke((2 * density).toInt(), 0xFF6C9BF2.toInt())
                    }
                }
                background = bg
                isClickable = true
                isFocusable = true
            }

            // Mini key preview
            val keyPreview = View(this).apply {
                val w = (24 * density).toInt()
                val h = (12 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(w, h).apply {
                    bottomMargin = (4 * density).toInt()
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 3 * density
                    setColor(keyColor)
                }
                background = bg
            }
            swatch.addView(keyPreview)

            val label = TextView(this).apply {
                text = themeManager.themeLabel(theme)
                setTextColor(textColor)
                textSize = 10f
                gravity = Gravity.CENTER
            }
            swatch.addView(label)

            swatch.setOnClickListener {
                themeManager.currentTheme = theme
                Toast.makeText(this, "Theme applied on next keyboard open", Toast.LENGTH_SHORT).show()
                setupThemePicker()
            }

            picker.addView(swatch)
        }

        // Restore the real theme for the manager
        themeManager.currentTheme = selected
    }

    // -- Host management --

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
        val passwordField = EditText(this).apply {
            hint = "Password (optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val keyPathField = EditText(this).apply { hint = "Private key path (optional)" }
        val uploadDirField = EditText(this).apply {
            hint = "Upload dir (default /tmp/keyjawn/)"
        }

        dialogView.addView(nameField)
        dialogView.addView(hostnameField)
        dialogView.addView(portField)
        dialogView.addView(usernameField)
        dialogView.addView(passwordField)
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
                val password = passwordField.text.toString().ifBlank { null }

                val host = HostConfig(
                    name = nameField.text.toString().trim(),
                    hostname = hostnameField.text.toString().trim(),
                    port = port,
                    username = usernameField.text.toString().trim(),
                    password = password,
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
                        setupThemePicker()
                        setupCustomCommandsButton()
                        refreshCommandSets()
                    }
                }
            }
        }
    }

    // -- Slash commands --

    private fun setupCustomCommandsButton() {
        val addBtn = findViewById<Button>(R.id.add_command_set_btn)
        if (billingManager.isFullVersion) {
            addBtn.visibility = View.VISIBLE
            addBtn.setOnClickListener { showAddCommandSetDialog() }
        } else {
            addBtn.visibility = View.GONE
        }
    }

    private fun refreshCommandSets() {
        commandSetsContainer.removeAllViews()
        val registry = slashCommandRegistry ?: return
        val sets = registry.getAvailableSets()
        val enabled = registry.getEnabledSets()
        val paid = billingManager.isFullVersion
        val density = resources.displayMetrics.density

        sets.forEach { (id, commandSet) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val checkbox = CheckBox(this).apply {
                text = "${commandSet.label} (${commandSet.commands.size})"
                setTextColor(0xFFDDDDDD.toInt())
                isChecked = id in enabled
                isEnabled = paid
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, checked ->
                    registry.setEnabled(id, checked)
                }
            }
            row.addView(checkbox)

            if (paid && registry.isCustomSet(id)) {
                val addCmdBtn = Button(this).apply {
                    text = "+"
                    textSize = 14f
                    val size = (36 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginStart = (4 * density).toInt()
                    }
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setOnClickListener { showAddCommandDialog(id) }
                }
                row.addView(addCmdBtn)

                val deleteBtn = Button(this).apply {
                    text = "X"
                    textSize = 14f
                    setTextColor(0xFFCC4444.toInt())
                    val size = (36 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginStart = (4 * density).toInt()
                    }
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Delete command set")
                            .setMessage("Delete \"${commandSet.label}\"?")
                            .setPositiveButton("Delete") { _, _ ->
                                registry.removeCustomSet(id)
                                refreshCommandSets()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                row.addView(deleteBtn)
            }

            commandSetsContainer.addView(row)

            // Show individual commands for custom sets (expandable)
            if (paid && registry.isCustomSet(id)) {
                for (command in commandSet.commands) {
                    val cmdRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val leftPad = (32 * density).toInt()
                        setPadding(leftPad, 0, 0, 0)
                    }
                    val cmdLabel = TextView(this).apply {
                        text = command
                        setTextColor(0xFFAAAAAA.toInt())
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    cmdRow.addView(cmdLabel)

                    val removeCmdBtn = Button(this).apply {
                        text = "x"
                        textSize = 12f
                        setTextColor(0xFF999999.toInt())
                        val size = (28 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        setPadding(0, 0, 0, 0)
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = 0
                        minimumHeight = 0
                        setOnClickListener {
                            registry.removeCommand(id, command)
                            refreshCommandSets()
                        }
                    }
                    cmdRow.addView(removeCmdBtn)

                    commandSetsContainer.addView(cmdRow)
                }
            }
        }
    }

    private fun showAddCommandSetDialog() {
        val registry = slashCommandRegistry ?: return
        val field = EditText(this).apply {
            hint = "Command set name"
            setPadding(48, 24, 48, 0)
        }

        AlertDialog.Builder(this)
            .setTitle("New command set")
            .setView(field)
            .setPositiveButton("Create") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotEmpty()) {
                    val id = "custom_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                    registry.addCustomSet(id, name)
                    refreshCommandSets()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCommandDialog(setId: String) {
        val registry = slashCommandRegistry ?: return
        val field = EditText(this).apply {
            hint = "Command (e.g. /my-command)"
            setPadding(48, 24, 48, 0)
        }

        AlertDialog.Builder(this)
            .setTitle("Add command")
            .setView(field)
            .setPositiveButton("Add") { _, _ ->
                var command = field.text.toString().trim()
                if (command.isNotEmpty()) {
                    if (!command.startsWith("/")) command = "/$command"
                    registry.addCommand(setId, command)
                    refreshCommandSets()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
