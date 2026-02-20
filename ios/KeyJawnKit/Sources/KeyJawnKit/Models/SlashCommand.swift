import Foundation

public struct SlashCommand: Sendable, Identifiable, Hashable {
    public let id: String          // e.g. "compact"
    public let trigger: String     // e.g. "/compact"
    public let description: String
    public let category: Category

    public enum Category: String, Sendable, CaseIterable {
        case session = "Session"
        case context = "Context"
        case files   = "Files"
        case shell   = "Shell"
        case custom  = "Custom"
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
        SlashCommand(id: "compact",   trigger: "/compact",   description: "Compact context",           category: .session),
        SlashCommand(id: "clear",     trigger: "/clear",     description: "Clear conversation",        category: .session),
        SlashCommand(id: "resume",    trigger: "/resume",    description: "Resume last session",       category: .session),
        SlashCommand(id: "help",      trigger: "/help",      description: "Show help",                 category: .session),
        SlashCommand(id: "review",    trigger: "/review",    description: "Review changes",            category: .session),
        SlashCommand(id: "add",       trigger: "/add",       description: "Add files to context",      category: .session),
        SlashCommand(id: "model",     trigger: "/model",     description: "Switch model",              category: .session),
        SlashCommand(id: "cost",      trigger: "/cost",      description: "Show session cost",         category: .session),
    ]

    /// Pre-loaded Aider commands.
    static let aider: [SlashCommand] = [
        SlashCommand(id: "aider-add",  trigger: "/add",   description: "Add file to context", category: .context),
        SlashCommand(id: "aider-drop", trigger: "/drop",  description: "Remove file",         category: .context),
        SlashCommand(id: "aider-run",  trigger: "/run",   description: "Run a shell command", category: .shell),
        SlashCommand(id: "aider-ask",  trigger: "/ask",   description: "Ask a question",      category: .context),
        SlashCommand(id: "aider-code", trigger: "/code",  description: "Request code change", category: .context),
        SlashCommand(id: "aider-git",  trigger: "/git",   description: "Run git command",     category: .shell),
    ]

    static let all: [SlashCommand] = claudeCode + aider
}
