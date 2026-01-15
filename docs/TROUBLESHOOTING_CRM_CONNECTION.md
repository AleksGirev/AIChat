# Troubleshooting CRM MCP Connection

## Problem

You're seeing logs like:
```
GIREV: ✗ Ticket search failed, falling back to getAllTickets
GIREV: ✗ getAllTickets also failed
GIREV: No tickets found for user
```

This means the Android app cannot connect to the CRM MCP server.

## Root Cause

The CRM MCP server runs as a **stdio process** (JSON-RPC over stdin/stdout), but Android apps cannot run stdio processes directly. You need an **HTTP bridge server** that wraps the stdio server and exposes REST endpoints.

## Solution: Set Up HTTP Bridge Server

### Option 1: Use Python HTTP Bridge (Recommended)

1. **Create `crm_mcp_bridge.py`** in your project root:

```python
#!/usr/bin/env python3
import subprocess
import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse
import threading
import os

class MCPBridgeHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, mcp_process=None, **kwargs):
        self.mcp_process = mcp_process
        super().__init__(*args, **kwargs)
    
    def log_message(self, format, *args):
        # Suppress default logging
        pass
    
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "service": "crm-mcp-bridge"}).encode())
        elif self.path == '/tools':
            # Return list of CRM tools
            tools = [
                {"name": "crm.getUserTickets", "description": "Get all tickets for a user"},
                {"name": "crm.getTicket", "description": "Get ticket by ID"},
                {"name": "crm.getUserInfo", "description": "Get user information"},
                {"name": "crm.searchTickets", "description": "Search tickets by query"}
            ]
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"tools": tools}).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_POST(self):
        # Parse path: /tools/{toolName}
        path_parts = self.path.strip('/').split('/')
        if len(path_parts) != 2 or path_parts[0] != 'tools':
            self.send_response(404)
            self.end_headers()
            return
        
        tool_name = path_parts[1]
        
        # Read request body
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)
        
        try:
            request_data = json.loads(body.decode())
            arguments = request_data.get('arguments', {})
            
            # Call MCP tool via stdio
            result = self.call_mcp_tool(tool_name, arguments)
            
            # Return response
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(result).encode())
        except Exception as e:
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())
    
    def call_mcp_tool(self, tool_name, arguments):
        """Call MCP tool via JSON-RPC over stdio"""
        if not self.mcp_process or self.mcp_process.poll() is not None:
            raise Exception("MCP server process is not running")
        
        # Build JSON-RPC request
        request = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments
            }
        }
        
        # Send request to MCP server
        request_json = json.dumps(request) + "\n"
        self.mcp_process.stdin.write(request_json.encode())
        self.mcp_process.stdin.flush()
        
        # Read response
        response_line = self.mcp_process.stdout.readline()
        if not response_line:
            raise Exception("No response from MCP server")
        
        response = json.loads(response_line.decode())
        
        if "error" in response:
            raise Exception(f"MCP error: {response['error']}")
        
        # Extract result content
        result_content = response.get("result", {}).get("content", [])
        return {"content": result_content}

if __name__ == '__main__':
    # Find project root (assuming script is in project root)
    project_root = os.path.dirname(os.path.abspath(__file__))
    console_agent_dir = os.path.join(project_root, "console-agent")
    
    # Build the JAR if needed
    print("Building console-agent...")
    build_result = subprocess.run(
        ["gradlew", ":console-agent:build"],
        cwd=project_root,
        capture_output=True
    )
    
    if build_result.returncode != 0:
        print("Warning: Build may have failed. Continuing anyway...")
    
    # Find the JAR file
    jar_path = None
    for root, dirs, files in os.walk(os.path.join(console_agent_dir, "build", "libs")):
        for file in files:
            if file.endswith(".jar") and "console-agent" in file:
                jar_path = os.path.join(root, file)
                break
        if jar_path:
            break
    
    if not jar_path:
        print("ERROR: Could not find console-agent JAR file")
        print("Please build it first: ./gradlew :console-agent:build")
        sys.exit(1)
    
    # Start MCP server process
    print(f"Starting CRM MCP server: {jar_path}")
    mcp_process = subprocess.Popen(
        ["java", "-jar", jar_path],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=console_agent_dir
    )
    
    # Send initialize request
    init_request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "crm-bridge",
                "version": "1.0.0"
            }
        }
    }
    
    try:
        mcp_process.stdin.write((json.dumps(init_request) + "\n").encode())
        mcp_process.stdin.flush()
        init_response = mcp_process.stdout.readline()
        print(f"Initialized: {init_response.decode()}")
    except Exception as e:
        print(f"Warning: Initialize may have failed: {e}")
    
    # Start HTTP server
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8003
    server = HTTPServer(
        ('0.0.0.0', port),
        lambda *args, **kwargs: MCPBridgeHandler(*args, mcp_process=mcp_process, **kwargs)
    )
    
    print(f"CRM MCP Bridge Server running on http://0.0.0.0:{port}")
    print(f"For Android Emulator: http://10.0.2.2:{port}")
    print("Press Ctrl+C to stop")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        mcp_process.terminate()
        server.shutdown()
```

2. **Make it executable:**
```bash
chmod +x crm_mcp_bridge.py
```

3. **Run the bridge server:**
```bash
python3 crm_mcp_bridge.py 8003
```

4. **Test the bridge:**
```bash
# Health check
curl http://localhost:8003/health

# List tools
curl http://localhost:8003/tools

# Test getUserTickets
curl -X POST http://localhost:8003/tools/crm.getUserTickets \
  -H "Content-Type: application/json" \
  -d '{"arguments": {"userId": "user1"}}'
```

### Option 2: Use Gradle Task + Manual Bridge

1. **Build the console-agent:**
```bash
./gradlew :console-agent:build
```

2. **Run CRM MCP server in one terminal:**
```bash
./gradlew :console-agent:runCrmMcpServer
```

3. **Set up HTTP bridge** (use the Python script above or create your own)

## Verify Connection

After starting the bridge server, check logs:

```bash
adb logcat | grep GIREV
```

You should see:
```
GIREV: === CrmMcpClient: initialize ===
GIREV: Initializing CRM MCP client via REST bridge: http://10.0.2.2:8003
GIREV: ✓ CRM MCP client initialized successfully
```

## Common Issues

### Issue 1: "Connection refused"
- **Cause**: Bridge server is not running
- **Fix**: Start the bridge server on port 8003

### Issue 2: "Timeout"
- **Cause**: Bridge server is running but MCP stdio process crashed
- **Fix**: Check bridge server logs, restart the bridge

### Issue 3: "404 Not Found"
- **Cause**: Wrong URL or bridge server not handling endpoints correctly
- **Fix**: Verify `Config.CRM_MCP_BRIDGE_URL` matches bridge server port

### Issue 4: "No response from MCP server"
- **Cause**: MCP stdio process is not responding
- **Fix**: Check if `runCrmMcpServer` is running, restart it

## Quick Test

1. **Start bridge server:**
```bash
python3 crm_mcp_bridge.py 8003
```

2. **In another terminal, test:**
```bash
curl http://localhost:8003/health
# Should return: {"status": "ok", "service": "crm-mcp-bridge"}
```

3. **Run Android app** and check logs:
```bash
adb logcat | grep GIREV
```

4. **Enable Support Mode** in the app and ask a question

You should now see:
```
GIREV: ✓ searchTickets successful: XXX chars
GIREV: ✓ Tickets context found: XXX chars
GIREV: Ticket IDs used: ticket1, ticket7, ...
```
