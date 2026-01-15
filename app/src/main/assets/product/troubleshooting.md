# Troubleshooting Guide

## Common Issues and Solutions

### Authentication Issues

#### Problem: Cannot log in
**Symptoms:**
- Login screen shows error message
- Credentials are rejected
- App crashes on login attempt

**Solutions:**
1. Verify your email/username and password are correct
2. Check if the user exists in the local database
3. Clear app data and try again (for development)
4. Check EncryptedSharedPreferences for stored auth state
5. Verify database migration completed successfully (version 8)

#### Problem: Authorization token expired
**Symptoms:**
- Session expires unexpectedly
- Need to log in repeatedly

**Solutions:**
1. Log out and log back in
2. Check token expiration settings
3. Verify EncryptedSharedPreferences is working correctly

#### Problem: User not found
**Symptoms:**
- "User not found" error message
- Cannot retrieve user information

**Solutions:**
1. Verify user exists in Room database (UserEntity table)
2. Check userId is correctly stored in EncryptedSharedPreferences
3. Ensure database migration included UserEntity

### Chat Issues

#### Problem: Messages not sending
**Symptoms:**
- Send button does nothing
- Messages stuck in "sending" state
- Network errors

**Solutions:**
1. Check internet connection
2. Verify API key is configured correctly in Config.kt
3. Check API endpoint is accessible
4. Review error logs for specific API errors
5. Try switching to a different model

#### Problem: No response from AI
**Symptoms:**
- Request sent but no response
- Timeout errors
- Empty response

**Solutions:**
1. Check API quota/rate limits
2. Verify model name is correct
3. Check request size (may exceed token limits)
4. Review API response in logs
5. Try reducing message history length

#### Problem: Responses are slow
**Symptoms:**
- Long wait times for responses
- UI freezes during requests

**Solutions:**
1. Check network latency
2. Disable RAG if not needed (reduces processing time)
3. Reduce message history sent to API
4. Use faster models for simple queries
5. Check if MCP tools are taking too long

### RAG Issues

#### Problem: RAG not finding relevant documents
**Symptoms:**
- Responses don't include documentation context
- "No relevant documents found" messages

**Solutions:**
1. Verify documents are indexed in RAG pipeline
2. Check vector database contains embeddings
3. Try different query phrasing
4. Verify document paths are correct
5. Re-index documents if needed

#### Problem: RAG search fails
**Symptoms:**
- Error messages about RAG search
- App crashes during RAG operations

**Solutions:**
1. Check vector database is accessible
2. Verify embedding model is working
3. Check database permissions
4. Review RAG pipeline logs
5. Rebuild vector index if corrupted

#### Problem: RAG context too large
**Symptoms:**
- Token limit exceeded
- Responses truncated

**Solutions:**
1. Reduce RAG context limit in settings
2. Enable reranking to get better chunks
3. Increase token limits if possible
4. Use more specific queries

### MCP Tool Issues

#### Problem: MCP tools not available
**Symptoms:**
- Tools not listed in chat
- "MCP not connected" errors

**Solutions:**
1. Verify MCP server is running
2. Check MCP server URL in Config.kt
3. Verify transport type is correct (HTTP/REST/WebSocket)
4. Check network connectivity to MCP server
5. Review MCP initialization logs

#### Problem: CRM tools not working
**Symptoms:**
- Cannot retrieve user tickets
- "Tool execution error" messages

**Solutions:**
1. Verify CRM MCP server is running
2. Check crm_data.json file exists and is valid
3. Verify userId is correctly passed to tools
4. Check JSON file permissions
5. Review CRM MCP server logs

#### Problem: Tool calls timeout
**Symptoms:**
- Tools take too long to execute
- Timeout errors

**Solutions:**
1. Check MCP server performance
2. Verify network latency
3. Optimize tool implementation
4. Increase timeout settings
5. Check if server is overloaded

### Database Issues

#### Problem: Database migration fails
**Symptoms:**
- App crashes on startup
- "Migration failed" errors
- Data loss

**Solutions:**
1. Check migration scripts are correct
2. Verify database version is updated
3. Use fallbackToDestructiveMigration for development (not production)
4. Backup data before migration
5. Check Room migration logs

#### Problem: Messages not saving
**Symptoms:**
- Messages disappear after app restart
- Database write errors

**Solutions:**
1. Check Room database is initialized
2. Verify write permissions
3. Check database is not locked
4. Review database transaction logs
5. Ensure proper error handling in repositories

#### Problem: User data not linked to sessions
**Symptoms:**
- Sessions don't show user information
- userId is null in sessions

**Solutions:**
1. Verify userId is stored after login
2. Check ChatSessionEntity has userId field
3. Ensure userId is passed when creating sessions
4. Verify database migration added userId column
5. Check foreign key relationships if used

### Support Chat Issues

#### Problem: Support responses not contextual
**Symptoms:**
- Generic responses without ticket context
- RAG context not included

**Solutions:**
1. Verify RAG index is populated
2. Check CRM MCP server is accessible
3. Verify userId is correctly retrieved
4. Check SupportContextBuilder is working
5. Review context building logs

#### Problem: Ticket history not retrieved
**Symptoms:**
- Support doesn't reference previous tickets
- "No tickets found" for user

**Solutions:**
1. Verify user has tickets in crm_data.json
2. Check userId matches between app and CRM
3. Verify CRM MCP server tools are working
4. Check JSON file structure is correct
5. Review ticket retrieval logs

### Performance Issues

#### Problem: App is slow
**Symptoms:**
- UI lag
- Slow response times
- High memory usage

**Solutions:**
1. Reduce message history length
2. Enable message compression
3. Optimize database queries
4. Check for memory leaks
5. Profile app performance

#### Problem: High token usage
**Symptoms:**
- API costs are high
- Token limits exceeded frequently

**Solutions:**
1. Enable message compression
2. Reduce RAG context size
3. Limit message history sent to API
4. Use more efficient models
5. Monitor token usage per conversation

## Debugging Tips

### Enable Logging
- Check Logcat for detailed error messages
- Enable verbose logging in development
- Review MCP server logs
- Check RAG pipeline logs

### Common Log Tags
- `ChatRepository`: Chat API calls
- `McpClient`: MCP communication
- `RAGPipeline`: RAG operations
- `ChatDatabase`: Database operations
- `SupportContextBuilder`: Support context building

### Testing Steps
1. Verify authentication works
2. Test basic chat functionality
3. Test RAG search with sample queries
4. Test MCP tool calling
5. Test support chat with ticket context
6. Verify database persistence

### Getting Help
If issues persist:
1. Check error logs for specific error messages
2. Review relevant documentation sections
3. Verify configuration is correct
4. Test with minimal setup
5. Contact support with error details
