import UIKit

@MainActor
public final class UploadPanel: UIView {

    public var onUpload: ((HostConfig) -> Void)?
    public var onDismiss: (() -> Void)?

    private let toolbar = UIView()
    private let titleLabel = UILabel()
    private let cancelButton = UIButton(type: .system)
    private let tableView = UITableView(frame: .zero, style: .plain)
    private let cellID = "HostCell"
    private let statusLabel = UILabel()

    private let theme: KeyboardTheme

    public var hosts: [HostConfig] = [] { didSet { tableView.reloadData() } }
    public var statusMessage: String = "" { didSet { statusLabel.text = statusMessage } }

    /// What the empty host list means, which is not always "you have no hosts".
    ///
    /// A keyboard extension without Full Access cannot open the App Group at all, so
    /// the host list the main app mirrored there reads back empty. Telling those users
    /// to add a host in the main app sends them to re-add hosts they already have;
    /// this distinguishes the two cases so the panel names the actual blocker.
    public enum EmptyReason {
        case noHostsConfigured
        case fullAccessRequired

        var message: String {
            switch self {
            case .noHostsConfigured:
                return "No hosts configured. Add one in the main app."
            case .fullAccessRequired:
                return "Turn on Full Access to reach your hosts: Settings → General → Keyboard → Keyboards → KeyJawn."
            }
        }
    }

    public var emptyReason: EmptyReason = .noHostsConfigured { didSet { tableView.reloadData() } }

    /// Gates host taps while the upload image is still being prepared off the
    /// main actor, so a tap before the data exists is a no-op instead of a crash.
    /// The host rows dim while disabled to show they are not tappable yet.
    public var isUploadEnabled: Bool = true {
        didSet { tableView.alpha = isUploadEnabled ? 1.0 : 0.5 }
    }

    public init(theme: KeyboardTheme = .dark) {
        self.theme = theme
        super.init(frame: .zero)
        setup()
    }

    public required init?(coder: NSCoder) { fatalError("use init(theme:)") }

    private func setup() {
        backgroundColor = theme.panelBg
        layer.cornerRadius = 12
        layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        clipsToBounds = true

        // Toolbar
        toolbar.translatesAutoresizingMaskIntoConstraints = false
        addSubview(toolbar)

        titleLabel.text = "SCP upload"
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = theme.panelText
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(titleLabel)

        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 15)
        cancelButton.setTitleColor(theme.accent, for: .normal)
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(cancelButton)

        let sep = UIView()
        sep.backgroundColor = theme.panelSeparator
        sep.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(sep)

        // Status
        statusLabel.text = ""
        statusLabel.font = .systemFont(ofSize: 12)
        statusLabel.textColor = theme.panelSecondaryText
        statusLabel.textAlignment = .center
        // The Full Access instruction is a full sentence with a settings path in it,
        // so give the status line room rather than truncating the fix.
        statusLabel.numberOfLines = 3
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(statusLabel)

        // Table
        tableView.dataSource = self
        tableView.delegate = self
        tableView.backgroundColor = .clear
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: cellID)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(tableView)

        NSLayoutConstraint.activate([
            toolbar.topAnchor.constraint(equalTo: topAnchor),
            toolbar.leadingAnchor.constraint(equalTo: leadingAnchor),
            toolbar.trailingAnchor.constraint(equalTo: trailingAnchor),
            toolbar.heightAnchor.constraint(equalToConstant: 44),

            titleLabel.centerXAnchor.constraint(equalTo: toolbar.centerXAnchor),
            titleLabel.centerYAnchor.constraint(equalTo: toolbar.centerYAnchor),

            cancelButton.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor, constant: -12),
            cancelButton.centerYAnchor.constraint(equalTo: toolbar.centerYAnchor),

            sep.leadingAnchor.constraint(equalTo: toolbar.leadingAnchor),
            sep.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor),
            sep.bottomAnchor.constraint(equalTo: toolbar.bottomAnchor),
            sep.heightAnchor.constraint(equalToConstant: 0.5),

            statusLabel.topAnchor.constraint(equalTo: toolbar.bottomAnchor, constant: 8),
            statusLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),

            tableView.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 4),
            tableView.leadingAnchor.constraint(equalTo: leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    @objc private func cancelTapped() { onDismiss?() }
}

extension UploadPanel: UITableViewDataSource, UITableViewDelegate {

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        max(hosts.count, 1)
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: cellID, for: indexPath)
        var config = cell.defaultContentConfiguration()
        if hosts.isEmpty {
            config.text = emptyReason.message
            config.textProperties.numberOfLines = 0
            config.textProperties.color = theme.panelSecondaryText
            cell.selectionStyle = .none
        } else {
            let host = hosts[indexPath.row]
            config.text = host.label.isEmpty ? host.hostname : host.label
            config.secondaryText = "\(host.username)@\(host.hostname):\(host.uploadPath)"
            config.textProperties.color = theme.panelText
            config.secondaryTextProperties.color = theme.panelSecondaryText
            cell.selectionStyle = .default
        }
        cell.contentConfiguration = config
        cell.backgroundColor = .clear
        let bg = UIView()
        bg.backgroundColor = theme.panelSelection
        cell.selectedBackgroundView = bg
        return cell
    }

    public func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        // The Full Access message wraps; let its row grow instead of clipping it.
        hosts.isEmpty ? UITableView.automaticDimension : 52
    }

    public func tableView(_ tableView: UITableView, estimatedHeightForRowAt indexPath: IndexPath) -> CGFloat { 52 }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard isUploadEnabled, !hosts.isEmpty else { return }
        KeyboardHaptics.keyPress()
        onUpload?(hosts[indexPath.row])
    }
}
