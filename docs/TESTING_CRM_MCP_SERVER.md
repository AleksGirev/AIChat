# Testing CRM MCP Server

## Understanding MCP Server Behavior

The CRM MCP server runs **continuously** - this is **normal and expected**. It's designed to:
- Read JSON-RPC requests from `stdin`
- Process requests and send responses to `stdout`
- Run indefinitely until stopped (Ctrl+C)

## Running the Server

```bash
./gradlew :console-agent:runCrmMcpServer
```

You should see:
```
Starting CRM MCP Server...
Server is ready. Waiting for JSON-RPC requests on stdin...
Press Ctrl+C to stop the server.
```

The server is now **waiting for input** - this is correct behavior!

## Testing the Server

### Test 1: Initialize Handshake

In the terminal where the server is running, type (or paste) this JSON-RPC request:

```json
{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}
```

Press Enter. You should see a response like:

```json
{"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"crm-mcp-server","version":"1.0.0"}}}
```

### Test 2: List Tools

Send this request:

```json
{"jsonrpc":"2.0","id":"2","method":"tools/list"}
```

You should see a response with all available CRM tools.

### Test 3: Get User Tickets

First, send the initialized notification:

```json
{"jsonrpc":"2.0","method":"notifications/initialized"}
```

Then call a tool:

```json
{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"crm.getUserTickets","arguments":{"userId":"user1"}}}
```

You should see a JSON response with user1's tickets.

## Using with Android App

For Android, you have two options:

### Option 1: HTTP Bridge Server (Recommended)

Create an HTTP bridge that wraps the stdio server. The Android app connects to the bridge via HTTP.

**Example Python Bridge** (simplified):

```python
#!/usr/bin/env python3
import subprocess
import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

class MCPBridgeHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, mcp_process=None, **kwargs):
        self.mcp_process = mcp_process
        super().__init__(*args, **kwargs)
    
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok"}).encode())
        elif self.path == '/tools':
            # Return list of tools
            tools = [
                {"name": "crm.getUserTickets", "description": "Get user tickets"},
                {"name": "crm.getTicket", "description": "Get ticket by ID"},
                {"name": "crm.getUserInfo", "description": "Get user info"},
                {"name": "crm.searchTickets", "description": "Search tickets"}
            ]
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"tools": tools}).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_POST(self):
        # Handle tool calls
        # Parse path: /tools/{toolName}
        # Read JSON body
        # Forward to MCP server via stdin
        # Return response
        pass

if __name__ == '__main__':
    # Start MCP server process
    mcp_process = subprocess.Popen(
        ['java', '-jar', 'console-agent/build/libs/console-agent-1.0.0.jar'],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )
    
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8003
    server = HTTPServer(('0.0.0.0', port), 
                       lambda *args, **kwargs: MCPBridgeHandler(*args, mcp_process=mcp_process, **kwargs))
    print(f"CRM MCP Bridge Server running on port {port}")
    server.serve_forever()
```

### Option 2: Test Without Bridge (Development)

For development/testing, you can:
1. Run the server in one terminal
2. Test manually with JSON-RPC requests
3. For Android app testing, use mock data or disable CRM features temporarily

## Stopping the Server

Press `Ctrl+C` in the terminal where the server is running.

## Troubleshooting

### Server doesn't respond

1. **Check if server started correctly:**
   - Look for "Server is ready" message
   - Check stderr for errors

2. **Verify JSON format:**
   - JSON must be on a single line
   - No trailing commas
   - Proper escaping of quotes

3. **Check data file:**
   - Ensure `console-agent/data/crm_data.json` exists
   - Verify JSON is valid: `cat console-agent/data/crm_data.json | python3 -m json.tool`

### Server exits immediately

- Check if there's an error in stderr
- Verify all dependencies are available
- Check file permissions for `crm_data.json`

### Android app can't connect

- **Emulator**: Use `http://10.0.2.2:8003` (already configured)
- **Physical device**: Use your computer's IP address
- Ensure HTTP bridge server is running
- Check firewall settings

## Quick Test Script

Save this as `test_crm_server.sh`:

```bash
#!/bin/bash

echo "Testing CRM MCP Server"
echo "======================"
echo ""

# Initialize
echo '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' | \
  ./gradlew :console-agent:runCrmMcpServer --console=plain 2>&1 | grep -v "BUILD"

echo ""
echo "Test complete!"
```

Run: `chmod +x test_crm_server.sh && ./test_crm_server.sh`

## Next Steps

1. **For Development**: Use the stdio server directly for testing
2. **For Android**: Set up HTTP bridge server (see Option 1 above)
3. **For Production**: Replace JSON file with real CRM backend

The server running indefinitely is **correct behavior** - it's waiting for requests! 🎉
