import UIKit

/// Overlay panel that presents slash commands as a scrollable list.
/// Add it to the keyboard extension's root view when the slash key is tapped,
/// then remove it when the user selects a command or dismisses.
@MainActor
public final class SlashCommandPanel: UIView {

    public var onSelect: ((SlashCommand) -> Void)?
    public var onDismiss: (() -> Void)?

    private let commands: [SlashCommand]
    private let table = UITableView(frame: .zero, style: .plain)

    public init(commands: [SlashCommand] = SlashCommand.all) {
        self.commands = commands
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("use init(commands:)") }

    // MARK: - Layout

    private func build() {
        backgroundColor = UIColor(red: 0.11, green: 0.11, blue: 0.11, alpha: 0.97)

        // Header row
        let header = buildHeader()
        header.translatesAutoresizingMaskIntoConstraints = false
        addSubview(header)

        // Divider
        let div = UIView()
        div.backgroundColor = UIColor(white: 0.25, alpha: 1)
        div.translatesAutoresizingMaskIntoConstraints = false
        addSubview(div)

        // Table
        table.dataSource = self
        table.delegate   = self
        table.backgroundColor   = .clear
        table.separatorColor    = UIColor(white: 0.2, alpha: 1)
        table.separatorInset    = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 0)
        table.rowHeight         = 44
        table.alwaysBounceVertical = false
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
        label.textColor = UIColor(white: 0.75, alpha: 1)
        label.font      = .systemFont(ofSize: 13, weight: .semibold)
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)

        let close = UIButton(type: .system)
        close.setImage(UIImage(systemName: "xmark"), for: .normal)
        close.tintColor = UIColor(white: 0.55, alpha: 1)
        close.addTarget(self, action: #selector(dismiss), for: .touchUpInside)
        close.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(close)

        NSLayoutConstraint.activate([
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),

            close.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            close.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            close.widthAnchor.constraint(equalToConstant: 30),
            close.heightAnchor.constraint(equalToConstant: 30),
        ])

        return view
    }

    @objc private func dismiss() {
        onDismiss?()
    }
}

// MARK: - UITableViewDataSource / Delegate

extension SlashCommandPanel: UITableViewDataSource, UITableViewDelegate {

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        commands.count
    }

    public func tableView(_ tableView: UITableView,
                          cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: CommandCell.id,
                                                for: indexPath) as! CommandCell
        cell.configure(with: commands[indexPath.row])
        return cell
    }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: false)
        onSelect?(commands[indexPath.row])
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
        selectedBackgroundView = {
            let v = UIView()
            v.backgroundColor = UIColor(white: 0.22, alpha: 1)
            return v
        }()

        trigger.font      = .monospacedSystemFont(ofSize: 14, weight: .semibold)
        trigger.textColor = UIColor(red: 0.4, green: 0.75, blue: 1.0, alpha: 1)
        trigger.translatesAutoresizingMaskIntoConstraints = false

        detail.font      = .systemFont(ofSize: 13, weight: .regular)
        detail.textColor = UIColor(white: 0.6, alpha: 1)
        detail.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(trigger)
        contentView.addSubview(detail)

        NSLayoutConstraint.activate([
            trigger.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            trigger.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            trigger.widthAnchor.constraint(equalToConstant: 100),

            detail.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            detail.leadingAnchor.constraint(equalTo: trigger.trailingAnchor, constant: 8),
            detail.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func configure(with cmd: SlashCommand) {
        trigger.text = cmd.trigger
        detail.text  = cmd.description
    }
}
