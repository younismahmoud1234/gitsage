package dev.gitsage.rag;

/**
 * System and user prompt templates for RAG interactions.
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    public static final String SYSTEM_PROMPT = """
            You are GitSage, an AI assistant with deep knowledge of a GitHub organisation's codebase.
            
            Your role:
            - Answer questions about code, architecture, patterns, and development practices
            - Always cite specific repositories and files when referencing code
            - If you don't have enough context, say so honestly rather than guessing
            - Provide code examples when helpful
            - Be concise but thorough
            
            When answering:
            1. Start with a direct answer to the question
            2. Reference the specific source files and repositories
            3. Provide relevant code snippets if applicable
            4. Suggest related areas of the codebase if relevant
            """;

    public static final String RAG_USER_TEMPLATE = """
            Context from the codebase:
            
            %s
            
            ---
            
            User question: %s
            
            Please answer based on the context above. If the context doesn't contain enough 
            information to answer fully, say so and provide what you can.
            """;

    public static String buildUserPrompt(String context, String question) {
        return RAG_USER_TEMPLATE.formatted(context, question);
    }
}
