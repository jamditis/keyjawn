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

    public var hosts: [HostConfig] = [] { didSet { tableView.reloadData() } }
    public var statusMessage: String = "" { didSet { statusLabel.text = statusMessage } }

    public override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    public required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        backgroundColor = UIColor(red: 0.13, green: 0.13, blue: 0.13, alpha: 0.97)
        layer.cornerRadius = 12
        layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        clipsToBounds = true

        // Toolbar
        toolbar.translatesAutoresizingMaskIntoConstraints = false
        addSubview(toolbar)

        titleLabel.text = "SCP Upload"
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(titleLabel)

        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 15)
        cancelButton.setTitleColor(.systemBlue, for: .normal)
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(cancelButton)

        let sep = UIView()
        sep.backgroundColor = UIColor.white.withAlphaComponent(0.15)
        sep.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(sep)

        // Status
        statusLabel.text = ""
        statusLabel.font = .systemFont(ofSize: 12)
        statusLabel.textColor = UIColor.white.withAlphaComponent(0.6)
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 2
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
            config.text = "No hosts configured. Add one in the main app."
            config.textProperties.color = UIColor.white.withAlphaComponent(0.4)
            cell.selectionStyle = .none
        } else {
            let host = hosts[indexPath.row]
            config.text = host.label.isEmpty ? host.hostname : host.label
            config.secondaryText = "\(host.username)@\(host.hostname):\(host.uploadPath)"
            config.textProperties.color = .white
            config.secondaryTextProperties.color = UIColor.white.withAlphaComponent(0.5)
            cell.selectionStyle = .default
        }
        cell.contentConfiguration = config
        cell.backgroundColor = .clear
        let bg = UIView()
        bg.backgroundColor = UIColor.white.withAlphaComponent(0.1)
        cell.selectedBackgroundView = bg
        return cell
    }

    public func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat { 52 }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !hosts.isEmpty else { return }
        onUpload?(hosts[indexPath.row])
    }
}
