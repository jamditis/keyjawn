import Foundation

/// A text shortcut offered by the slash command panel.
///
/// Selecting one inserts `trigger` as plain text into whatever field has focus. There
/// is no integration with, or link to, any other application — the panel is a text
/// autocomplete list in the same sense as the system keyboard's predictive bar.
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

// MARK: - Built-in commands

public extension SlashCommand {

    /// The shortcuts the panel offers, grouped by what they do.
    ///
    /// This used to be two lists concatenated, which put `/help` and `/clear` in the
    /// panel twice with identical descriptions and no way to tell the rows apart.
    /// Triggers are unique here, and `SlashCommandTests` keeps them that way.
    static let all: [SlashCommand] = [
        SlashCommand(id: "help",    trigger: "/help",    description: "Show help",              category: .session),
        SlashCommand(id: "clear",   trigger: "/clear",   description: "Clear conversation",     category: .session),
        SlashCommand(id: "compact", trigger: "/compact", description: "Compact context",        category: .session),
        SlashCommand(id: "resume",  trigger: "/resume",  description: "Resume last session",    category: .session),
        SlashCommand(id: "model",   trigger: "/model",   description: "Switch model",           category: .session),
        SlashCommand(id: "cost",    trigger: "/cost",    description: "Show session cost",      category: .session),
        SlashCommand(id: "quit",    trigger: "/quit",    description: "Quit",                   category: .session),

        SlashCommand(id: "add",     trigger: "/add",     description: "Add files to context",   category: .context),
        SlashCommand(id: "chat",    trigger: "/chat",    description: "Switch to chat mode",    category: .context),
        SlashCommand(id: "code",    trigger: "/code",    description: "Switch to code mode",    category: .context),

        SlashCommand(id: "review",  trigger: "/review",  description: "Review changes",         category: .files),
        SlashCommand(id: "diff",    trigger: "/diff",    description: "Show pending diff",      category: .files),

        SlashCommand(id: "tools",   trigger: "/tools",   description: "List available tools",   category: .shell),
    ]

    /// `all` bucketed into the categories that are actually populated, in the order
    /// `Category.allCases` declares. The panel renders one section per entry, which is
    /// what makes a list this long scannable — and what finally puts the `category`
    /// field to use after it sat unread since it was introduced.
    static var grouped: [(category: Category, commands: [SlashCommand])] {
        Category.allCases.compactMap { category in
            let matching = all.filter { $0.category == category }
            return matching.isEmpty ? nil : (category, matching)
        }
    }
}
