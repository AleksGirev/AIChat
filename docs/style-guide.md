# Coding Conventions & Style Guide

## Kotlin Style

### General Principles

1. **Idiomatic Kotlin**: Prefer Kotlin idioms over Java patterns
2. **Readability First**: Code should be self-documenting
3. **Null Safety**: Leverage Kotlin's null safety features
4. **Immutability**: Prefer `val` over `var`, immutable collections
5. **Extension Functions**: Use for utility operations

### Naming Conventions

- **Classes**: PascalCase (`ChatViewModel`, `McpRepository`)
- **Functions**: camelCase (`sendMessage`, `loadSession`)
- **Variables**: camelCase (`currentSessionId`, `isLoading`)
- **Constants**: UPPER_SNAKE_CASE (`DEFAULT_MODEL`, `MAX_TOKENS`)
- **Packages**: lowercase, no underscores (`com.example.aichat.ui`)

### Code Organization

#### File Structure
```kotlin
package com.example.aichat.ui.viewmodel

import ... // Standard library
import ... // Android
import ... // Third-party
import ... // Project imports

/**
 * Class-level documentation
 */
class ChatViewModel {
    // Companion object (if any)
    companion object { }
    
    // Properties
    private val _state = MutableStateFlow(...)
    val state: StateFlow<...> = _state.asStateFlow()
    
    // Initialization
    init { }
    
    // Public functions
    fun publicMethod() { }
    
    // Private functions
    private fun privateMethod() { }
}
```

#### Function Ordering
1. Companion object
2. Properties (public, then private)
3. Initialization blocks
4. Public functions
5. Private functions

### Kotlin-Specific Patterns

#### Use `when` for Multi-way Branches
```kotlin
// ✅ Good
when (screen) {
    is Screen.Chat -> ChatScreen(...)
    is Screen.Settings -> SettingsScreen(...)
    else -> ErrorScreen()
}

// ❌ Avoid
if (screen == Screen.Chat) { ... }
else if (screen == Screen.Settings) { ... }
```

#### Prefer `sealed class` for State
```kotlin
// ✅ Good
sealed class Screen {
    object Chat : Screen()
    object Settings : Screen()
    data class Detail(val id: String) : Screen()
}
```

#### Use `Result` for Error Handling
```kotlin
// ✅ Good
suspend fun loadData(): Result<Data> {
    return try {
        val data = api.getData()
        Result.success(data)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

#### Extension Functions for Utilities
```kotlin
// ✅ Good
fun String.truncate(maxLength: Int): String {
    return if (length > maxLength) {
        take(maxLength) + "..."
    } else {
        this
    }
}
```

### Coroutines Best Practices

#### Use `suspend` for Async Operations
```kotlin
// ✅ Good
suspend fun fetchData(): Result<Data> = withContext(Dispatchers.IO) {
    // IO operation
}

// ❌ Avoid
fun fetchData(callback: (Result<Data>) -> Unit) { }
```

#### Prefer `StateFlow` over `LiveData`
```kotlin
// ✅ Good
private val _messages = MutableStateFlow<List<Message>>(emptyList())
val messages: StateFlow<List<Message>> = _messages.asStateFlow()

// ❌ Avoid (unless necessary for Android-specific features)
private val _messages = MutableLiveData<List<Message>>()
```

#### Structured Concurrency
```kotlin
// ✅ Good
viewModelScope.launch {
    val result = repository.fetchData()
    // Handle result
}

// ❌ Avoid (unscoped coroutines)
GlobalScope.launch { }
```

### Android-Specific Patterns

#### ViewModel Pattern
```kotlin
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.sendMessage(text)
            // Handle result
        }
    }
}
```

#### Compose Best Practices
```kotlin
// ✅ Good - Stateless composable
@Composable
fun ChatScreen(
    messages: List<Message>,
    onSendClick: (String) -> Unit
) {
    // UI code
}

// ❌ Avoid - Stateful composable (unless necessary)
@Composable
fun ChatScreen() {
    var messages by remember { mutableStateOf(emptyList<Message>()) }
    // ...
}
```

### Error Handling

#### Use `Result` Type
```kotlin
suspend fun loadData(): Result<Data> {
    return try {
        val data = api.getData()
        Result.success(data)
    } catch (e: HttpException) {
        Result.failure(Exception("HTTP Error: ${e.code()}"))
    } catch (e: IOException) {
        Result.failure(Exception("Network Error: ${e.message}"))
    } catch (e: Exception) {
        Result.failure(Exception("Unexpected Error: ${e.message}"))
    }
}
```

#### Handle Errors in ViewModel
```kotlin
fun sendMessage(text: String) {
    viewModelScope.launch {
        _isLoading.value = true
        repository.sendMessage(text)
            .onSuccess { response ->
                _messages.value = _messages.value + response
            }
            .onFailure { error ->
                _errorMessage.value = error.message
            }
        _isLoading.value = false
    }
}
```

### Documentation

#### KDoc for Public APIs
```kotlin
/**
 * Sends a chat message to the LLM and returns the response.
 * 
 * @param message The user's message text
 * @param conversationHistory Optional conversation history for context
 * @param model The model to use (default: YandexGPT)
 * @return Result containing the assistant's response or an error
 */
suspend fun sendMessage(
    message: String,
    conversationHistory: List<ChatMessage> = emptyList(),
    model: String = DEFAULT_MODEL
): Result<String>
```

#### Inline Comments for Complex Logic
```kotlin
// Summarize messages every 10 messages to reduce token usage
if (messageCount % 10 == 0) {
    summarizeMessages()
}
```

### Testing Conventions

#### Unit Tests
```kotlin
class ChatRepositoryTest {
    @Test
    fun `sendMessage returns success when API call succeeds`() {
        // Given
        val mockApi = mock<OpenAiApiService>()
        val repository = ChatRepository(mockApi, ...)
        
        // When
        val result = repository.sendMessage("Hello")
        
        // Then
        assertTrue(result.isSuccess)
    }
}
```

### Dependency Injection

#### Koin Module Structure
```kotlin
val appModule = module {
    // Network dependencies
    single<OkHttpClient> { NetworkModule.provideOkHttpClient() }
    
    // Database
    single { ChatDatabase.getDatabase(androidContext()) }
    
    // Repositories
    single { ChatRepository(...) }
    
    // ViewModels
    viewModel { ChatViewModel(...) }
}
```

### File Organization

- One class per file (except data classes/sealed classes in same file)
- Related classes in same package
- Keep files under 500 lines (split if needed)
- Use meaningful file names matching class names

### Imports

- Group imports: stdlib, Android, third-party, project
- Use explicit imports (avoid `import com.example.*`)
- Remove unused imports

### Formatting

- 4 spaces for indentation (not tabs)
- No trailing whitespace
- Maximum line length: 120 characters
- Blank line between logical sections
- Blank line before function/class definitions

