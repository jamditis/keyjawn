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

    /// Pre-loaded Gemini CLI commands.
    static let gemini: [SlashCommand] = [
        SlashCommand(id: "gemini-help",  trigger: "/help",   description: "Show help",             category: .session),
        SlashCommand(id: "gemini-clear", trigger: "/clear",  description: "Clear conversation",    category: .session),
        SlashCommand(id: "gemini-chat",  trigger: "/chat",   description: "Switch to chat mode",   category: .session),
        SlashCommand(id: "gemini-code",  trigger: "/code",   description: "Switch to code mode",   category: .session),
        SlashCommand(id: "gemini-quit",  trigger: "/quit",   description: "Quit",                  category: .session),
        SlashCommand(id: "gemini-tools", trigger: "/tools",  description: "List available tools",  category: .session),
    ]

    static let all: [SlashCommand] = claudeCode + gemini
}
