# Authorization Guide

## Overview

This guide explains the authorization flow in AIChat, common authorization issues, and how to resolve them.

## Authorization Flow

### User Authentication

1. **Login Screen**: User enters email/username and password
2. **Validation**: Credentials are validated against local database
3. **Session Creation**: User session is created and stored
4. **Token Storage**: User ID is stored in EncryptedSharedPreferences
5. **Database Link**: User ID is linked to chat sessions

### Authentication Components

- **AuthScreen.kt**: UI for login/registration
- **AuthViewModel.kt**: Handles authentication logic
- **UserEntity**: Room database entity for users
- **EncryptedSharedPreferences**: Secure storage for auth tokens

### Registration Flow

1. User enters email, username, and password
2. User entity is created in Room database
3. User ID is generated (UUID)
4. User is automatically logged in
5. Session is established

## Common Authorization Issues

### Issue 1: Cannot Log In

**Symptoms:**
- Login fails with "Invalid credentials"
- User not found errors

**Causes:**
1. User doesn't exist in database
2. Password mismatch
3. Database migration issues
4. EncryptedSharedPreferences corruption

**Solutions:**
1. **Verify User Exists:**
   ```kotlin
   // Check UserEntity in database
   val user = userDao.getUserByEmail(email)
   if (user == null) {
       // User doesn't exist, need to register
   }
   ```

2. **Check Password:**
   - For MVP: Simple string comparison
   - For production: Use proper password hashing (bcrypt, Argon2)

3. **Database Issues:**
   - Verify database version is 8 (includes UserEntity)
   - Check migration completed successfully
   - Clear app data if needed (development only)

4. **EncryptedSharedPreferences:**
   - Verify encryption keys are working
   - Check if preferences are accessible
   - Re-initialize if corrupted

### Issue 2: Session Expires Unexpectedly

**Symptoms:**
- User logged out automatically
- Need to log in repeatedly
- Session not persisting

**Causes:**
1. EncryptedSharedPreferences cleared
2. App data cleared
3. Token expiration logic
4. Database issues

**Solutions:**
1. **Check Token Storage:**
   ```kotlin
   val userId = encryptedPrefs.getString("user_id", null)
   if (userId == null) {
       // Session lost, redirect to login
   }
   ```

2. **Session Persistence:**
   - Verify EncryptedSharedPreferences is not cleared
   - Check app lifecycle handling
   - Ensure proper session management

3. **Token Expiration:**
   - For MVP: No expiration (local auth)
   - For production: Implement refresh tokens

### Issue 3: User ID Not Linked to Sessions

**Symptoms:**
- Chat sessions don't show user info
- userId is null in ChatSessionEntity
- Cannot retrieve user-specific data

**Causes:**
1. userId not stored after login
2. Database migration didn't add userId column
3. Session created before login
4. userId not passed when creating sessions

**Solutions:**
1. **Store User ID After Login:**
   ```kotlin
   // In AuthViewModel after successful login
   encryptedPrefs.edit()
       .putString("user_id", user.userId)
       .apply()
   ```

2. **Database Migration:**
   - Verify ChatSessionEntity has userId field (nullable)
   - Check migration script adds userId column
   - Ensure backward compatibility (nullable field)

3. **Session Creation:**
   ```kotlin
   // When creating new session
   val userId = getCurrentUserId() // From EncryptedSharedPreferences
   val session = ChatSessionEntity(
       id = UUID.randomUUID().toString(),
       userId = userId, // Link to user
       title = "...",
       // ...
   )
   ```

### Issue 4: Authorization Errors in API Calls

**Symptoms:**
- API calls fail with 401 Unauthorized
- "Invalid API key" errors
- Token expired messages

**Causes:**
1. API key not configured
2. IAM token expired (YandexGPT)
3. Invalid API credentials
4. Network issues

**Solutions:**
1. **Check API Configuration:**
   ```kotlin
   // In Config.kt
   const val YANDEX_IAM_TOKEN = "your-token"
   const val OPENAI_API_KEY = "your-key"
   ```

2. **YandexGPT IAM Token:**
   - IAM tokens expire (typically 12 hours)
   - Refresh token before expiration
   - Get new token from: https://cloud.yandex.ru/docs/iam/operations/iam-token/create

3. **API Key Validation:**
   - Verify keys are correct
   - Check for typos or extra spaces
   - Test API keys independently

4. **Error Handling:**
   ```kotlin
   when (response.code()) {
       401 -> "Invalid API key or token expired"
       403 -> "Insufficient permissions"
       else -> "API error: ${response.code()}"
   }
   ```

## Authorization Best Practices

### Security

1. **Password Storage:**
   - Never store plain text passwords
   - Use proper hashing (bcrypt recommended)
   - Salt passwords before hashing

2. **Token Storage:**
   - Use EncryptedSharedPreferences for sensitive data
   - Don't store tokens in plain SharedPreferences
   - Clear tokens on logout

3. **API Keys:**
   - Don't hardcode in source code (use BuildConfig)
   - Use environment variables or secure storage
   - Rotate keys regularly

### Error Handling

1. **Graceful Degradation:**
   ```kotlin
   try {
       val userId = getCurrentUserId()
       // Use userId
   } catch (e: Exception) {
       // Fallback: redirect to login
       navigateToLogin()
   }
   ```

2. **User Feedback:**
   - Show clear error messages
   - Provide actionable solutions
   - Don't expose sensitive information

3. **Logging:**
   - Log errors for debugging
   - Don't log sensitive data (passwords, tokens)
   - Use appropriate log levels

### Testing

1. **Test Scenarios:**
   - Login with valid credentials
   - Login with invalid credentials
   - Registration flow
   - Session persistence
   - Logout functionality

2. **Edge Cases:**
   - Empty credentials
   - Special characters in username
   - Very long passwords
   - Database migration during login

## Troubleshooting Steps

1. **Check Authentication State:**
   ```kotlin
   val isAuthenticated = encryptedPrefs.contains("user_id")
   ```

2. **Verify User in Database:**
   ```kotlin
   val user = userDao.getUserByEmail(email)
   Log.d("Auth", "User found: ${user != null}")
   ```

3. **Check Session Linkage:**
   ```kotlin
   val sessions = sessionDao.getSessionsByUserId(userId)
   Log.d("Auth", "User has ${sessions.size} sessions")
   ```

4. **Review Logs:**
   - Check Logcat for authentication errors
   - Look for database migration issues
   - Verify API call errors

## Support Integration

When users report authorization issues:

1. **Check Ticket History:**
   - Use CRM MCP tool: `crm.getUserTickets(userId)`
   - Look for previous authorization issues
   - Reference similar tickets

2. **Use Documentation:**
   - RAG search for "authorization" in docs
   - Provide relevant troubleshooting steps
   - Reference authorization guide

3. **Personalized Response:**
   - Consider user's previous tickets
   - Provide context-specific solutions
   - Follow up if issue persists

## Future Enhancements

- OAuth2 integration
- Social login (Google, Apple)
- Multi-factor authentication
- Password reset flow
- Account recovery
- Session management UI
- Token refresh automation
