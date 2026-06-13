import UIKit

@MainActor
public final class ClipboardPanel: UIView, UITableViewDataSource, UITableViewDelegate {

    // MARK: - Public callbacks

    /// Called when the user taps a clipboard item. Caller should insert the text and dismiss.
    public var onSelect: ((String) -> Void)?

    /// Called when the Done button is tapped.
    public var onDismiss: (() -> Void)?

    // MARK: - Private views

    private let toolbar = UIView()
    private let titleLabel = UILabel()
    private let doneButton = UIButton(type: .system)
    private let tableView = UITableView(frame: .zero, style: .grouped)

    private let cellReuseID = "ClipCell"

    // MARK: - Section constants

    private enum Section: Int, CaseIterable {
        case pinned = 0
        case recent = 1

        var title: String {
            switch self {
            case .pinned: return "Pinned"
            case .recent: return "Recent"
            }
        }

        var emptyMessage: String {
            switch self {
            case .pinned: return "Nothing pinned yet"
            case .recent: return "No history yet"
            }
        }
    }

    // MARK: - Init

    public override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    public required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }

    // MARK: - Setup

    private func setupView() {
        backgroundColor = UIColor(red: 0.13, green: 0.13, blue: 0.13, alpha: 0.97)

        // Round only the top corners
        layer.cornerRadius = 12
        layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        clipsToBounds = true

        setupToolbar()
        setupTableView()
        setupConstraints()
    }

    private func setupToolbar() {
        toolbar.translatesAutoresizingMaskIntoConstraints = false
        addSubview(toolbar)

        titleLabel.text = "Clipboard"
        titleLabel.font = UIFont.systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(titleLabel)

        doneButton.setTitle("Done", for: .normal)
        doneButton.titleLabel?.font = UIFont.systemFont(ofSize: 15, weight: .regular)
        doneButton.setTitleColor(.systemBlue, for: .normal)
        doneButton.addTarget(self, action: #selector(doneTapped), for: .touchUpInside)
        doneButton.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(doneButton)

        // Thin separator below toolbar
        let separator = UIView()
        separator.backgroundColor = UIColor.white.withAlphaComponent(0.15)
        separator.translatesAutoresizingMaskIntoConstraints = false
        toolbar.addSubview(separator)

        NSLayoutConstraint.activate([
            titleLabel.centerXAnchor.constraint(equalTo: toolbar.centerXAnchor),
            titleLabel.centerYAnchor.constraint(equalTo: toolbar.centerYAnchor),

            doneButton.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor, constant: -12),
            doneButton.centerYAnchor.constraint(equalTo: toolbar.centerYAnchor),

            separator.leadingAnchor.constraint(equalTo: toolbar.leadingAnchor),
            separator.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor),
            separator.bottomAnchor.constraint(equalTo: toolbar.bottomAnchor),
            separator.heightAnchor.constraint(equalToConstant: 0.5),
        ])
    }

    private func setupTableView() {
        tableView.dataSource = self
        tableView.delegate = self
        tableView.backgroundColor = .clear
        tableView.separatorInset = .zero
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: cellReuseID)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(tableView)
    }

    private func setupConstraints() {
        NSLayoutConstraint.activate([
            toolbar.topAnchor.constraint(equalTo: topAnchor),
            toolbar.leadingAnchor.constraint(equalTo: leadingAnchor),
            toolbar.trailingAnchor.constraint(equalTo: trailingAnchor),
            toolbar.heightAnchor.constraint(equalToConstant: 44),

            tableView.topAnchor.constraint(equalTo: toolbar.bottomAnchor),
            tableView.leadingAnchor.constraint(equalTo: leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    // MARK: - Public

    /// Reload the table to reflect the current clipboard history state.
    /// Call this each time before showing the panel.
    public func refresh() {
        tableView.reloadData()
    }

    // MARK: - Actions

    @objc private func doneTapped() {
        onDismiss?()
    }

    // MARK: - Data helpers

    private func rows(for section: Section) -> [String] {
        switch section {
        case .pinned: return ClipboardHistory.shared.pinned
        case .recent: return ClipboardHistory.shared.items
        }
    }

    private func section(at index: Int) -> Section {
        Section(rawValue: index) ?? .recent
    }

    private func isEmptyPlaceholder(at indexPath: IndexPath) -> Bool {
        rows(for: section(at: indexPath.section)).isEmpty
    }

    // MARK: - UITableViewDataSource

    public func numberOfSections(in tableView: UITableView) -> Int {
        Section.allCases.count
    }

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        let rows = rows(for: self.section(at: section))
        // Always show at least 1 row (the empty-state message)
        return max(rows.count, 1)
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: cellReuseID, for: indexPath)
        let sec = section(at: indexPath.section)
        let rowData = rows(for: sec)

        var config = cell.defaultContentConfiguration()
        config.textProperties.numberOfLines = 1
        config.textProperties.lineBreakMode = .byTruncatingTail
        config.textProperties.color = .white
        config.textProperties.font = UIFont.monospacedSystemFont(ofSize: 13, weight: .regular)

        if rowData.isEmpty {
            config.text = sec.emptyMessage
            config.textProperties.color = UIColor.white.withAlphaComponent(0.4)
            cell.selectionStyle = .none
        } else {
            config.text = rowData[indexPath.row]
            cell.selectionStyle = .default
        }

        cell.contentConfiguration = config
        cell.backgroundColor = .clear

        // Selection highlight color
        let selectedBG = UIView()
        selectedBG.backgroundColor = UIColor.white.withAlphaComponent(0.1)
        cell.selectedBackgroundView = selectedBG

        return cell
    }

    // MARK: - UITableViewDelegate — heights and headers

    public func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        44
    }

    public func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        28
    }

    public func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let sec = self.section(at: section)
        let container = UIView()
        container.backgroundColor = .clear

        let label = UILabel()
        label.text = sec.title.uppercased()
        label.font = UIFont.systemFont(ofSize: 11, weight: .semibold)
            .withSmallCaps
        label.textColor = UIColor.white.withAlphaComponent(0.5)
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)

        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            label.centerYAnchor.constraint(equalTo: container.centerYAnchor),
        ])

        return container
    }

    // MARK: - UITableViewDelegate — selection

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !isEmptyPlaceholder(at: indexPath) else { return }
        let text = rows(for: section(at: indexPath.section))[indexPath.row]
        onSelect?(text)
    }

    // MARK: - UITableViewDelegate — swipe actions

    public func tableView(
        _ tableView: UITableView,
        trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath
    ) -> UISwipeActionsConfiguration? {
        guard !isEmptyPlaceholder(at: indexPath) else { return nil }

        let sec = section(at: indexPath.section)
        let text = rows(for: sec)[indexPath.row]

        let deleteAction = UIContextualAction(style: .destructive, title: "Delete") { _, _, completion in
            switch sec {
            case .pinned:
                ClipboardHistory.shared.unpin(text)
            case .recent:
                ClipboardHistory.shared.remove(at: indexPath.row)
            }
            tableView.reloadData()
            completion(true)
        }
        deleteAction.backgroundColor = .systemRed

        switch sec {
        case .pinned:
            let unpinAction = UIContextualAction(style: .normal, title: "Unpin") { _, _, completion in
                ClipboardHistory.shared.unpin(text)
                tableView.reloadData()
                completion(true)
            }
            unpinAction.backgroundColor = .systemOrange
            return UISwipeActionsConfiguration(actions: [deleteAction, unpinAction])

        case .recent:
            let pinAction = UIContextualAction(style: .normal, title: "Pin") { _, _, completion in
                ClipboardHistory.shared.pin(text)
                tableView.reloadData()
                completion(true)
            }
            pinAction.backgroundColor = .systemBlue
            return UISwipeActionsConfiguration(actions: [deleteAction, pinAction])
        }
    }
}

// MARK: - UIFont small caps helper

private extension UIFont {
    var withSmallCaps: UIFont {
        let features: [[UIFontDescriptor.FeatureKey: Any]] = [
            [
                .type: kLowerCaseType,
                .selector: kLowerCaseSmallCapsSelector,
            ]
        ]
        let descriptor = fontDescriptor.addingAttributes([.featureSettings: features])
        return UIFont(descriptor: descriptor, size: pointSize)
    }
}
