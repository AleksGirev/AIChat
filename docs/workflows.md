# Common Workflows

## How to Add a New Screen

### 1. Create the Screen Composable

Create a new file in `app/src/main/java/com/example/aichat/ui/[feature]/[Feature]Screen.kt`:

```kotlin
package com.example.aichat.ui.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feature") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        // Screen content
        Column(modifier = Modifier.padding(padding)) {
            // Your UI here
        }
    }
}
```

### 2. Create the ViewModel

Create `app/src/main/java/com/example/aichat/ui/viewmodel/FeatureViewModel.kt`:

```kotlin
package com.example.aichat.ui.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FeatureState(
    val isLoading: Boolean = false,
    val data: List<Item> = emptyList(),
    val errorMessage: String? = null
)

class FeatureViewModel(
    private val repository: FeatureRepository
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FeatureState())
    val state: StateFlow<FeatureState> = _state.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            repository.getData()
                .onSuccess { data ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        data = data
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )
                }
        }
    }
}
```

### 3. Add Screen to Navigation

Update `app/src/main/java/com/example/aichat/ui/navigation/Navigation.kt`:

```kotlin
sealed class Screen {
    // ... existing screens
    object Feature : Screen()
}

@Composable
fun AppNavigation(...) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    
    when (currentScreen) {
        // ... existing cases
        is Screen.Feature -> {
            FeatureScreen(
                onBackClick = { currentScreen = Screen.Chat }
            )
        }
    }
}
```

### 4. Add Navigation Route

Add a way to navigate to the new screen (e.g., from `ChatScreen`):

```kotlin
Button(onClick = { onFeatureClick() }) {
    Text("Open Feature")
}
```

### 5. Register ViewModel in Koin (if using DI)

Update `app/src/main/java/com/example/aichat/di/AppModule.kt`:

```kotlin
val appModule = module {
    // ... existing dependencies
    
    viewModel { FeatureViewModel(get()) }
}
```

## How to Add a New API Endpoint

### 1. Define API Interface

Update or create `app/src/main/java/com/example/aichat/data/api/[Service]ApiService.kt`:

```kotlin
interface FeatureApiService {
    @POST("api/endpoint")
    suspend fun callEndpoint(
        @Body request: FeatureRequest
    ): Response<FeatureResponse>
}
```

### 2. Add to NetworkModule

Update `app/src/main/java/com/example/aichat/data/network/NetworkModule.kt`:

```kotlin
fun provideFeatureApiService(retrofit: Retrofit): FeatureApiService {
    return retrofit.create(FeatureApiService::class.java)
}
```

### 3. Register in Koin

Update `app/src/main/java/com/example/aichat/di/AppModule.kt`:

```kotlin
single {
    val retrofit = NetworkModule.provideRetrofit(get(), get())
    NetworkModule.provideFeatureApiService(retrofit)
}
```

### 4. Use in Repository

```kotlin
class FeatureRepository(
    private val apiService: FeatureApiService
) {
    suspend fun getData(): Result<Data> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.callEndpoint(request)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## How to Add a New Database Entity

### 1. Create Entity Class

Create `app/src/main/java/com/example/aichat/data/local/FeatureEntity.kt`:

```kotlin
@Entity(tableName = "features")
data class FeatureEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2. Create DAO

Create `app/src/main/java/com/example/aichat/data/local/FeatureDao.kt`:

```kotlin
@Dao
interface FeatureDao {
    @Query("SELECT * FROM features")
    suspend fun getAll(): List<FeatureEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FeatureEntity)
    
    @Delete
    suspend fun delete(entity: FeatureEntity)
}
```

### 3. Add to Database

Update `app/src/main/java/com/example/aichat/data/local/ChatDatabase.kt`:

```kotlin
@Database(
    entities = [
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        FeatureEntity::class  // Add here
    ],
    version = 2  // Increment version
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun featureDao(): FeatureDao  // Add here
}
```

### 4. Create Repository

Create `app/src/main/java/com/example/aichat/data/local/FeatureRepository.kt`:

```kotlin
class FeatureRepository(
    private val dao: FeatureDao
) {
    suspend fun getAll(): List<FeatureEntity> = dao.getAll()
    suspend fun insert(entity: FeatureEntity) = dao.insert(entity)
    suspend fun delete(entity: FeatureEntity) = dao.delete(entity)
}
```

### 5. Register in Koin

Update `app/src/main/java/com/example/aichat/di/AppModule.kt`:

```kotlin
single { get<ChatDatabase>().featureDao() }
single { FeatureRepository(get()) }
```

## How to Add a New MCP Tool

### 1. Ensure MCP Server is Running

The MCP server should expose tools via the MCP protocol. Tools are discovered automatically.

### 2. Use Tool in Repository

Tools are automatically available via `McpRepository`:

```kotlin
class FeatureRepository(
    private val mcpRepository: McpRepository
) {
    suspend fun useTool(): Result<String> {
        return mcpRepository.callTool(
            toolName = "tool_name",
            arguments = mapOf("param" to "value")
        ).map { result ->
            result.content.joinToString("\n") { it.text ?: "" }
        }
    }
}
```

### 3. Tools in LLM Context

Tools are automatically included in LLM requests when:
- `enableTools = true` in `ChatRepository.sendChatRequest()`
- MCP repository is connected
- LLM can decide to call tools based on user query

## How to Add a New RAG Document Source

### 1. Index Documents

In the console-agent, use the RAG pipeline:

```kotlin
val ragPipeline = RAGPipeline.create(
    dbPath = "rag_index.db",
    ollamaBaseUrl = "http://localhost:11434",
    ollamaModel = "nomic-embed-text"
)

ragPipeline.processDocuments(
    paths = listOf("path/to/docs", "path/to/more/docs")
)
```

### 2. Search in Agent

The RAG pipeline automatically searches indexed documents:

```kotlin
val results = ragPipeline.search(
    queryText = "How do I add a screen?",
    limit = 10,
    minSimilarity = 0.3f
)
```

## How to Debug

### Android App

1. **Logging**: Use `android.util.Log`
   ```kotlin
   Log.d("TAG", "Debug message")
   ```

2. **Breakpoints**: Set breakpoints in Android Studio

3. **Network**: Use OkHttp logging interceptor (already configured)

### Console Agent

1. **Print Statements**: Use `println()` for console output

2. **Error Handling**: All functions return `Result<T>` - check with `.onFailure { }`

3. **MCP Debugging**: Check MCP server logs, verify tools are available

## How to Test

### Unit Tests

Create test files in `app/src/test/java/`:

```kotlin
class ChatRepositoryTest {
    @Test
    fun `sendMessage returns success`() {
        // Test implementation
    }
}
```

### Integration Tests

Create test files in `app/src/androidTest/java/`:

```kotlin
@RunWith(AndroidJUnit4::class)
class ChatRepositoryIntegrationTest {
    @Test
    fun testSendMessage() {
        // Integration test
    }
}
```

## How to Deploy

### Build Release APK

```bash
./gradlew :app:assembleRelease
```

### Build Console Agent JAR

```bash
./gradlew :console-agent:installDist
```

Output: `console-agent/build/install/console-agent/bin/console-agent`

