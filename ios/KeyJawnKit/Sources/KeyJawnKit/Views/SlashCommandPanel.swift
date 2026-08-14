import UIKit

/// Overlay panel that presents slash commands as a grouped, scrollable list.
/// Add it to the keyboard extension's root view when the slash key is tapped,
/// then remove it when the user selects a command or dismisses.
///
/// Selecting a row inserts its trigger as plain text into the focused field. The
/// panel neither launches nor links to anything else.
@MainActor
public final class SlashCommandPanel: UIView {

    public var onSelect: ((SlashCommand) -> Void)?
    public var onDismiss: (() -> Void)?

    private let sections: [(category: SlashCommand.Category, commands: [SlashCommand])]
    private let theme: KeyboardTheme
    private let table = UITableView(frame: .zero, style: .plain)

    public init(commands: [SlashCommand] = SlashCommand.all, theme: KeyboardTheme = .dark) {
        // Group by category so a list this long stays scannable, preserving the
        // declared order within each group.
        self.sections = SlashCommand.Category.allCases.compactMap { category in
            let matching = commands.filter { $0.category == category }
            return matching.isEmpty ? nil : (category, matching)
        }
        self.theme = theme
        super.init(frame: .zero)
        accessibilityIdentifier = "slash-command-panel"
        build()
    }

    required init?(coder: NSCoder) { fatalError("use init(commands:theme:)") }

    // MARK: - Layout

    private func build() {
        backgroundColor = theme.panelBg

        // Header row
        let header = buildHeader()
        header.translatesAutoresizingMaskIntoConstraints = false
        addSubview(header)

        // Divider
        let div = UIView()
        div.backgroundColor = theme.panelSeparator
        div.translatesAutoresizingMaskIntoConstraints = false
        addSubview(div)

        // Table
        table.dataSource = self
        table.delegate   = self
        table.backgroundColor   = .clear
        table.separatorColor    = theme.panelSeparator
        table.separatorInset    = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 0)
        table.rowHeight         = 44
        table.sectionHeaderTopPadding = 0
        table.alwaysBounceVertical = true
        table.keyboardDismissMode = .none
        table.register(CommandCell.self, forCellReuseIdentifier: CommandCell.id)
        table.translatesAutoresizingMaskIntoConstraints = false
        addSubview(table)

        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: topAnchor),
            header.leadingAnchor.constraint(equalTo: leadingAnchor),
            header.trailingAnchor.constraint(equalTo: trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: 40),

            div.topAnchor.constraint(equalTo: header.bottomAnchor),
            div.leadingAnchor.constraint(equalTo: leadingAnchor),
            div.trailingAnchor.constraint(equalTo: trailingAnchor),
            div.heightAnchor.constraint(equalToConstant: 1),

            table.topAnchor.constraint(equalTo: div.bottomAnchor),
            table.leadingAnchor.constraint(equalTo: leadingAnchor),
            table.trailingAnchor.constraint(equalTo: trailingAnchor),
            table.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    private func buildHeader() -> UIView {
        let view = UIView()
        view.backgroundColor = .clear

        let label = UILabel()
        label.text      = "Slash commands"
        label.textColor = theme.panelSecondaryText
        label.font      = .systemFont(ofSize: 13, weight: .semibold)
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)

        let close = UIButton(type: .system)
        close.setImage(UIImage(systemName: "xmark"), for: .normal)
        close.tintColor = theme.panelSecondaryText
        close.accessibilityLabel = "Close slash commands"
        close.addTarget(self, action: #selector(dismiss), for: .touchUpInside)
        close.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(close)

        NSLayoutConstraint.activate([
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),

            close.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            close.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            close.widthAnchor.constraint(equalToConstant: 44),
            close.heightAnchor.constraint(equalToConstant: 36),
        ])

        return view
    }

    @objc private func dismiss() {
        onDismiss?()
    }
}

// MARK: - UITableViewDataSource / Delegate

extension SlashCommandPanel: UITableViewDataSource, UITableViewDelegate {

    public func numberOfSections(in tableView: UITableView) -> Int {
        sections.count
    }

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        sections[section].commands.count
    }

    public func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        26
    }

    public func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let container = UIView()
        container.backgroundColor = .clear

        let label = UILabel()
        label.text = sections[section].category.rawValue.uppercased()
        label.font = .systemFont(ofSize: 11, weight: .semibold)
        label.textColor = theme.panelSecondaryText
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)

        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 14),
            label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -4),
        ])

        return container
    }

    public func tableView(_ tableView: UITableView,
                          cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: CommandCell.id,
                                                 for: indexPath) as! CommandCell
        cell.configure(with: sections[indexPath.section].commands[indexPath.row], theme: theme)
        return cell
    }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: false)
        KeyboardHaptics.keyPress()
        onSelect?(sections[indexPath.section].commands[indexPath.row])
    }
}

// MARK: - CommandCell

@MainActor
private final class CommandCell: UITableViewCell {

    static let id = "CommandCell"

    private let trigger  = UILabel()
    private let detail   = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear

        trigger.font = .monospacedSystemFont(ofSize: 14, weight: .semibold)
        trigger.translatesAutoresizingMaskIntoConstraints = false
        trigger.setContentCompressionResistancePriority(.required, for: .horizontal)

        detail.font = .systemFont(ofSize: 13, weight: .regular)
        detail.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(trigger)
        contentView.addSubview(detail)

        NSLayoutConstraint.activate([
            trigger.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            trigger.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            // Reserve a column so the descriptions line up, but let a longer trigger
            // grow past it instead of being clipped — the fixed 100pt width this
            // replaced would truncate anything longer than "/compact".
            trigger.widthAnchor.constraint(greaterThanOrEqualToConstant: 92),

            detail.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            detail.leadingAnchor.constraint(equalTo: trigger.trailingAnchor, constant: 8),
            detail.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func configure(with cmd: SlashCommand, theme: KeyboardTheme) {
        trigger.text = cmd.trigger
        trigger.textColor = theme.accent
        detail.text  = cmd.description
        detail.textColor = theme.panelSecondaryText

        let selected = UIView()
        selected.backgroundColor = theme.panelSelection
        selectedBackgroundView = selected

        accessibilityLabel = "\(cmd.trigger), \(cmd.description)"
    }
}
