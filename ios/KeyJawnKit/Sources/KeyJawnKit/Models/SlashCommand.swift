import Foundation

public struct SlashCommand: Sendable, Identifiable, Hashable {
    public let id: String          // e.g. "compact"
    public let trigger: String     // e.g. "/compact"
    public let description: String
    public let category: Category

    public enum Category: String, Sendable, CaseIterable {
        case claudeCode = "Claude Code"
        case aider      = "Aider"
        case codex      = "Codex"
        case shell      = "Shell"
        case custom     = "Custom"
    }

    public init(id: String, trigger: String, description: String, category: Category) {
        self.id = id
        self.trigger = trigger
        self.description = description
        self.category = category
    }
}

// MARK: - Built-in command sets

public extension SlashCommand {
    /// Pre-loaded Claude Code commands.
    static let claudeCode: [SlashCommand] = [
        SlashCommand(id: "compact",   trigger: "/compact",   description: "Compact context",           category: .claudeCode),
        SlashCommand(id: "clear",     trigger: "/clear",     description: "Clear conversation",        category: .claudeCode),
        SlashCommand(id: "resume",    trigger: "/resume",    description: "Resume last session",       category: .claudeCode),
        SlashCommand(id: "help",      trigger: "/help",      description: "Show help",                 category: .claudeCode),
        SlashCommand(id: "review",    trigger: "/review",    description: "Review changes",            category: .claudeCode),
        SlashCommand(id: "add",       trigger: "/add",       description: "Add files to context",      category: .claudeCode),
        SlashCommand(id: "model",     trigger: "/model",     description: "Switch model",              category: .claudeCode),
        SlashCommand(id: "cost",      trigger: "/cost",      description: "Show session cost",         category: .claudeCode),
    ]

    /// Pre-loaded Aider commands.
    static let aider: [SlashCommand] = [
        SlashCommand(id: "aider-add",  trigger: "/add",   description: "Add file to context", category: .aider),
        SlashCommand(id: "aider-drop", trigger: "/drop",  description: "Remove file",         category: .aider),
        SlashCommand(id: "aider-run",  trigger: "/run",   description: "Run a shell command", category: .aider),
        SlashCommand(id: "aider-ask",  trigger: "/ask",   description: "Ask a question",      category: .aider),
        SlashCommand(id: "aider-code", trigger: "/code",  description: "Request code change", category: .aider),
        SlashCommand(id: "aider-git",  trigger: "/git",   description: "Run git command",     category: .aider),
    ]

    static let all: [SlashCommand] = claudeCode + aider
}
