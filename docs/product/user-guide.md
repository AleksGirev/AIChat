# User Guide

## Getting Started

### Installation

1. Download and install AIChat from the app store or build from source
2. Launch the application
3. Complete the initial setup

### First Launch

1. **Authentication**: You'll be prompted to log in or register
   - If you have an account: Enter your email/username and password
   - If you're new: Tap "Register" to create an account

2. **Permissions**: Grant necessary permissions if prompted
   - Storage (for database)
   - Network (for API calls)

3. **Configuration**: The app will initialize:
   - Database setup
   - RAG index (if available)
   - MCP connections

## Basic Usage

### Starting a Chat

1. **New Chat**: Tap the "+" button or "New Chat" option
2. **Type Message**: Enter your question or message in the text field
3. **Send**: Tap the send button or press Enter
4. **Wait for Response**: The AI will process your request and respond

### Chat Features

- **Message History**: Previous messages are shown in the conversation
- **Session Management**: Each chat is a separate session
- **Message Actions**: Long-press messages for options (copy, delete, etc.)

### Switching Models

1. Open chat settings (gear icon)
2. Select "Model" option
3. Choose from available models:
   - YandexGPT
   - OpenRouter models
4. New messages will use the selected model

## Support Chat

### Using Support Features

The support chat provides contextual help based on:
- Product documentation
- Your previous support tickets
- Common troubleshooting guides

### Asking for Help

1. **Start Support Chat**: Use the support chat interface
2. **Ask Your Question**: Type your question naturally
   - Example: "Why isn't authorization working?"
   - Example: "How do I use the API?"
3. **Get Contextual Response**: The AI will:
   - Search documentation for relevant information
   - Check your previous tickets
   - Provide personalized assistance

### Understanding Responses

- **Documentation References**: Responses may reference product docs
- **Ticket History**: If you have related tickets, they'll be considered
- **Contextual Help**: Answers are tailored to your situation

## Advanced Features

### RAG (Retrieval-Augmented Generation)

RAG enhances responses by searching through indexed documentation.

**When RAG is Active:**
- Responses include information from product docs
- More accurate and contextual answers
- References to documentation sources

**Indicators:**
- UI may show "Using documentation" indicator
- Responses reference specific documentation sections

### MCP Tools

MCP tools extend the AI's capabilities.

**Available Tools:**
- **CRM Tools**: Access your support tickets
- **Search Tools**: Search the web or documentation
- **Repository Tools**: Access code repositories (if configured)

**How It Works:**
- AI automatically decides when to use tools
- Tool results are included in responses
- No manual tool selection needed

### Message Compression

For long conversations, older messages are compressed to save tokens.

**What Happens:**
- Older messages are summarized
- Summaries maintain context
- Original messages are preserved in database

**Benefits:**
- Faster responses
- Lower API costs
- Maintains conversation context

## Managing Your Account

### Profile Settings

1. Open settings (menu or gear icon)
2. Select "Profile" or "Account"
3. View or edit:
   - Username
   - Email
   - Account information

### Logout

1. Open settings
2. Tap "Logout" or "Sign Out"
3. Confirm logout
4. You'll be returned to the login screen

### Data Management

- **Chat History**: Stored locally on your device
- **Sessions**: Organized by date and title
- **Export**: (Coming soon) Export chat history

## Troubleshooting

### Common Issues

#### App Won't Start
- Force close and restart
- Clear app cache
- Reinstall if necessary

#### Messages Not Sending
- Check internet connection
- Verify API configuration
- Try switching models

#### Slow Responses
- Check network speed
- Reduce message history
- Disable RAG if not needed

#### Can't Log In
- Verify credentials
- Check if account exists
- Try registration if new user

### Getting Help

1. **Use Support Chat**: Ask questions in the support interface
2. **Check Documentation**: Review product documentation
3. **Check Tickets**: View your previous support tickets
4. **Report Issues**: Contact support with error details

## Tips and Best Practices

### Writing Effective Queries

1. **Be Specific**: Clear questions get better answers
   - Good: "How do I configure YandexGPT API?"
   - Bad: "API help"

2. **Provide Context**: Include relevant information
   - Good: "I'm getting 401 errors when calling the API with my token"
   - Bad: "API broken"

3. **Use Natural Language**: Ask questions naturally
   - The AI understands conversational queries
   - No need for special syntax

### Optimizing Performance

1. **Message History**: Keep conversations focused
2. **Model Selection**: Use faster models for simple queries
3. **RAG Usage**: Enable RAG for documentation questions
4. **Tool Usage**: Let AI decide when to use tools

### Privacy and Security

1. **Local Storage**: Conversations stored on device
2. **API Calls**: Direct to LLM services (no intermediaries)
3. **Authentication**: Secure token storage
4. **Data Control**: You control your data

## Keyboard Shortcuts

- **Send Message**: Enter or Return key
- **New Chat**: Ctrl+N (if supported)
- **Settings**: Menu key or settings icon

## Accessibility

- **Screen Readers**: Compatible with TalkBack
- **Text Size**: Respects system text size settings
- **High Contrast**: Supports high contrast mode

## Updates and Changes

- **Automatic Updates**: App updates via app store
- **Release Notes**: Check update descriptions
- **Feature Announcements**: In-app notifications for new features

## Feedback

We welcome your feedback:
- **In-App**: Use support chat to provide feedback
- **Issues**: Report bugs with detailed information
- **Suggestions**: Share feature requests

## Additional Resources

- **Product Documentation**: Full documentation in app
- **API Documentation**: Technical API reference
- **Troubleshooting Guide**: Common issues and solutions
- **FAQ**: Frequently asked questions

---

**Note**: This guide covers the MVP version. Additional features and improvements are planned for future releases.
