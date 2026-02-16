package com.keyjawn

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    private lateinit var themeManager: ThemeManager

    private val isFull: Boolean
        get() = BuildConfig.FLAVOR == "full"

    private val density: Float
        get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        themeManager = ThemeManager(this)

        applyThemeColors()

        commandSetsContainer = findViewById(R.id.command_sets)

        setupUpgradeButton()
        setupHapticToggle()
        setupTooltipToggle()
        setupThemePicker()
        setupKeyMapping()

        val hostSection = findViewById<LinearLayout>(R.id.host_section)
        if (isFull) {
            hostStorage = HostStorage(this)
            hostListContainer = findViewById(R.id.host_list)
            styleAccentButton(findViewById(R.id.add_host_btn))
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

        val flavorLabel = if (isFull) "" else " lite"
        val versionText = findViewById<TextView>(R.id.version_text)
        versionText.text = "KeyJawn$flavorLabel v${BuildConfig.VERSION_NAME}"
        versionText.setTextColor(themeManager.keyHint())

        refreshCommandSets()
    }

    // -- Theme application --

    private fun applyThemeColors() {
        val tm = themeManager

        // Root background
        findViewById<View>(R.id.settings_root).setBackgroundColor(tm.keyboardBg())

        // Title
        findViewById<TextView>(R.id.settings_title).setTextColor(tm.keyText())

        // Section headers
        for (id in listOf(R.id.toggles_header, R.id.theme_header, R.id.key_mapping_header,
            R.id.host_header, R.id.commands_header)) {
            findViewById<TextView>(id).setTextColor(tm.keyHint())
        }

        // Toggle labels
        findViewById<TextView>(R.id.haptic_label).setTextColor(tm.keyText())
        findViewById<TextView>(R.id.tooltip_label).setTextColor(tm.keyText())

        // Card backgrounds
        for (id in listOf(R.id.toggles_card, R.id.theme_card, R.id.key_mapping_card,
            R.id.host_card, R.id.commands_card)) {
            applyCardBackground(findViewById(id))
        }

        // Dividers
        for (id in listOf(R.id.divider_1, R.id.divider_2, R.id.divider_3, R.id.divider_4)) {
            findViewById<View>(id).setBackgroundColor(tm.divider())
        }
    }

    private fun applyCardBackground(view: View) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(themeManager.keyBg())
        }
        view.background = bg
    }

    private fun styleAccentButton(button: Button) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(themeManager.accent())
        }
        button.background = bg
        button.setTextColor(0xFFFFFFFF.toInt())
    }

    private fun styleOutlineButton(button: Button) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6 * density
            setColor(0x00000000)
            setStroke((1 * density + 0.5f).toInt(), themeManager.accent())
        }
        button.background = bg
        button.setTextColor(themeManager.accent())
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

    // -- Tooltip toggle (paid only) --

    private fun setupTooltipToggle() {
        val section = findViewById<LinearLayout>(R.id.tooltip_section)
        if (!isFull) {
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE
        val appPrefs = AppPrefs(this)
        val toggle = findViewById<CheckBox>(R.id.tooltip_toggle)
        toggle.isChecked = appPrefs.isTooltipsEnabled()
        toggle.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.setTooltipsEnabled(isChecked)
        }
    }

    // -- Theme picker --

    private fun setupThemePicker() {
        val themeSection = findViewById<LinearLayout>(R.id.theme_section)
        if (!isFull) {
            themeSection.visibility = View.GONE
            return
        }
        themeSection.visibility = View.VISIBLE

        val picker = findViewById<LinearLayout>(R.id.theme_picker)
        picker.removeAllViews()

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
                        setStroke((2 * density).toInt(), themeManager.accent())
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
                // Re-apply theme colors to settings page too
                applyThemeColors()
                setupThemePicker()
            }

            picker.addView(swatch)
        }

        // Restore the real theme for the manager
        themeManager.currentTheme = selected
    }

    // -- Key mapping --

    private fun setupKeyMapping() {
        val section = findViewById<LinearLayout>(R.id.key_mapping_section)
        if (!isFull) {
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE
        val listContainer = findViewById<LinearLayout>(R.id.key_mapping_list)
        refreshKeyMapping(listContainer)
    }

    private fun refreshKeyMapping(listContainer: LinearLayout) {
        listContainer.removeAllViews()
        val appPrefs = AppPrefs(this)

        for (slot in 0..2) {
            val slotLabel = when (slot) {
                0 -> "Slot 1 (left)"
                1 -> "Slot 2 (center)"
                2 -> "Slot 3 (right)"
                else -> "Slot $slot"
            }
            val currentValue = AppPrefs.getExtraSlotLabel(appPrefs.getExtraSlot(slot))
            addKeyMappingRow(listContainer, slotLabel, currentValue) { selectedValue ->
                appPrefs.setExtraSlot(slot, selectedValue)
                refreshKeyMapping(listContainer)
            }
        }

        val quickKeyValue = appPrefs.getQuickKey().let {
            if (it.startsWith("text:")) it.removePrefix("text:") else it
        }
        addKeyMappingRow(listContainer, "Quick key", quickKeyValue, isQuickKey = true) { selectedValue ->
            appPrefs.setQuickKey(selectedValue)
            refreshKeyMapping(listContainer)
        }
    }

    private fun addKeyMappingRow(
        container: LinearLayout,
        label: String,
        currentValue: String,
        isQuickKey: Boolean = false,
        onSelect: (String) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * density).toInt()
            )
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(themeManager.keyText())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelView)

        val valueView = TextView(this).apply {
            text = currentValue
            textSize = 15f
            setTextColor(themeManager.accent())
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
        }
        row.addView(valueView)

        val changeBtn = Button(this).apply {
            text = "Change"
            textSize = 12f
            isAllCaps = false
            val w = (80 * density).toInt()
            val h = (36 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(w, h)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
        }
        styleOutlineButton(changeBtn)

        changeBtn.setOnClickListener {
            if (isQuickKey) {
                showQuickKeyPickerDialog(onSelect)
            } else {
                showSlotPickerDialog(onSelect)
            }
        }
        row.addView(changeBtn)

        container.addView(row)
    }

    private fun showSlotPickerDialog(onSelect: (String) -> Unit) {
        val options = AppPrefs.EXTRA_SLOT_OPTIONS
        val labels = options.map { AppPrefs.getExtraSlotLabel(it) }.toTypedArray()
        val items = labels.toMutableList()
        items.add("Custom...")
        AlertDialog.Builder(this)
            .setTitle("Choose key")
            .setItems(items.toTypedArray()) { _, which ->
                if (which < options.size) {
                    onSelect(options[which])
                } else {
                    showCustomTextDialog(onSelect)
                }
            }
            .show()
    }

    private fun showQuickKeyPickerDialog(onSelect: (String) -> Unit) {
        val options = AppPrefs.QUICK_KEY_OPTIONS
        val items = options.toMutableList()
        items.add("Custom...")
        AlertDialog.Builder(this)
            .setTitle("Choose quick key")
            .setItems(items.toTypedArray()) { _, which ->
                if (which < options.size) {
                    onSelect(options[which])
                } else {
                    showCustomTextDialog(onSelect)
                }
            }
            .show()
    }

    private fun showCustomTextDialog(onSelect: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Type custom key (max 8 chars)"
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            setPadding(48, 24, 48, 0)
        }
        AlertDialog.Builder(this)
            .setTitle("Custom key")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val sanitized = AppPrefs.sanitizeCustomText(input.text.toString())
                if (sanitized.isNotEmpty()) {
                    onSelect("text:$sanitized")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            nameText.setTextColor(themeManager.keyText())
            detailText.text = "${host.username}@${host.hostname}:${host.port}"
            detailText.setTextColor(themeManager.keyHint())
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

    private fun setupUpgradeButton() {
        val upgradeBtn = findViewById<Button>(R.id.upgrade_btn)
        if (isFull) {
            upgradeBtn.visibility = View.GONE
        } else {
            upgradeBtn.text = "Get full version ($4)"
            upgradeBtn.visibility = View.VISIBLE
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(0xFF2E7D32.toInt())
            }
            upgradeBtn.background = bg
            upgradeBtn.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://keyjawn.amditis.tech/pricing")))
            }
        }
    }

    // -- Slash commands --

    private fun setupCustomCommandsButton() {
        val addBtn = findViewById<Button>(R.id.add_command_set_btn)
        if (isFull) {
            addBtn.visibility = View.VISIBLE
            styleAccentButton(addBtn)
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
        val paid = isFull

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
                setTextColor(themeManager.keyText())
                isChecked = id in enabled
                isEnabled = paid || !registry.isCustomSet(id)
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
                styleOutlineButton(addCmdBtn)
                row.addView(addCmdBtn)

                val deleteBtn = Button(this).apply {
                    text = "X"
                    textSize = 14f
                    setTextColor(themeManager.accentLocked())
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
                val deleteBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6 * density
                    setColor(0x00000000)
                    setStroke((1 * density + 0.5f).toInt(), themeManager.accentLocked())
                }
                deleteBtn.background = deleteBg
                row.addView(deleteBtn)
            }

            commandSetsContainer.addView(row)

            // Show individual commands for custom sets
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
                        setTextColor(themeManager.keyHint())
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    cmdRow.addView(cmdLabel)

                    val removeCmdBtn = Button(this).apply {
                        text = "x"
                        textSize = 12f
                        setTextColor(themeManager.keyHint())
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
