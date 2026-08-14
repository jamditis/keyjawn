import Foundation

public enum SlashCommandStoreError: Error, Equatable {
    case invalidTrigger
    case duplicateTrigger
}

/// Custom slash shortcuts in the App Group. Triggers are user strings only.
public struct SlashCommandStore: Sendable {
    public static let defaultsKey = "keyjawn.customSlashCommands"

    public struct Record: Codable, Sendable, Equatable {
        public var id: String
        public var trigger: String
        public var description: String

        public init(id: String, trigger: String, description: String) {
            self.id = id
            self.trigger = trigger
            self.description = description
        }

        public var command: SlashCommand {
            SlashCommand(id: id, trigger: trigger, description: description, category: .custom)
        }
    }

    public static func load(from defaults: UserDefaults) -> [Record] {
        guard let data = defaults.data(forKey: defaultsKey),
              let records = try? JSONDecoder().decode([Record].self, from: data) else {
            return []
        }
        return records
    }

    public static func commands(from defaults: UserDefaults) -> [SlashCommand] {
        load(from: defaults).map(\.command)
    }

    public static func save(_ records: [Record], to defaults: UserDefaults) {
        guard let data = try? JSONEncoder().encode(records) else { return }
        defaults.set(data, forKey: defaultsKey)
    }

    public static func validateTrigger(
        _ raw: String,
        builtIns: [SlashCommand] = SlashCommand.all,
        existing: [Record]
    ) -> Result<String, SlashCommandStoreError> {
        var trigger = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trigger.hasPrefix("/") {
            trigger = "/" + trigger
        }
        guard trigger.count > 1,
              !trigger.contains(" "),
              trigger.unicodeScalars.allSatisfy({ CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "/-_")).contains($0) })
        else {
            return .failure(.invalidTrigger)
        }
        let used = Set(builtIns.map(\.trigger) + existing.map(\.trigger))
        if used.contains(trigger) {
            return .failure(.duplicateTrigger)
        }
        return .success(trigger)
    }

    public static func add(
        trigger: String,
        description: String,
        to defaults: UserDefaults,
        builtIns: [SlashCommand] = SlashCommand.all
    ) -> Result<Record, SlashCommandStoreError> {
        var records = load(from: defaults)
        switch validateTrigger(trigger, builtIns: builtIns, existing: records) {
        case .failure(let error):
            return .failure(error)
        case .success(let normalized):
            let record = Record(
                id: UUID().uuidString,
                trigger: normalized,
                description: description.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            records.append(record)
            save(records, to: defaults)
            return .success(record)
        }
    }

    public static func remove(id: String, from defaults: UserDefaults) {
        var records = load(from: defaults)
        records.removeAll { $0.id == id }
        save(records, to: defaults)
    }

    public static func appGroupDefaults() -> UserDefaults {
        UserDefaults(suiteName: AppGroupConfig.suiteName) ?? .standard
    }
}
