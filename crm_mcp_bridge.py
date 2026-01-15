#!/usr/bin/env python3
"""
CRM MCP HTTP Bridge Server

This server wraps the CRM MCP stdio server and exposes REST endpoints
for Android app to connect to.

Usage:
    python3 crm_mcp_bridge.py [port]
    
Default port: 8003
"""

import subprocess
import json
import sys
import os
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse
import threading
import time

class MCPBridgeHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, mcp_process=None, **kwargs):
        self.mcp_process = mcp_process
        self.request_id = 1
        super().__init__(*args, **kwargs)
    
    def log_message(self, format, *args):
        # Custom logging
        print(f"[{self.address_string()}] {format % args}")
    
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = {
                "status": "ok",
                "service": "crm-mcp-bridge",
                "mcp_running": self.mcp_process is not None and self.mcp_process.poll() is None
            }
            self.wfile.write(json.dumps(response).encode())
        elif self.path == '/tools':
            # Return list of CRM tools
            tools = [
                {
                    "name": "crm.getUserTickets",
                    "description": "Get all tickets for a user",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "userId": {
                                "type": "string",
                                "description": "User ID"
                            }
                        },
                        "required": ["userId"]
                    }
                },
                {
                    "name": "crm.getTicket",
                    "description": "Get ticket by ID",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "ticketId": {
                                "type": "string",
                                "description": "Ticket ID"
                            }
                        },
                        "required": ["ticketId"]
                    }
                },
                {
                    "name": "crm.getUserInfo",
                    "description": "Get user information",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "userId": {
                                "type": "string",
                                "description": "User ID"
                            }
                        },
                        "required": ["userId"]
                    }
                },
                {
                    "name": "crm.searchTickets",
                    "description": "Search tickets by query",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": {
                                "type": "string",
                                "description": "Search query"
                            },
                            "userId": {
                                "type": "string",
                                "description": "Optional user ID to filter by"
                            }
                        },
                        "required": ["query"]
                    }
                }
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
        if content_length == 0:
            self.send_response(400)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Empty request body"}).encode())
            return
        
        body = self.rfile.read(content_length)
        
        try:
            request_data = json.loads(body.decode())
            arguments = request_data.get('arguments', {})
            
            print(f"[REQUEST] Tool: {tool_name}, Arguments: {arguments}")
            
            # Call MCP tool via stdio
            result = self.call_mcp_tool(tool_name, arguments)
            
            print(f"[RESPONSE] Tool: {tool_name}, Success: {not result.get('isError', False)}")
            
            # Return response in format expected by RestMcpTransport
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(result).encode())
        except Exception as e:
            print(f"[ERROR] Tool: {tool_name}, Error: {str(e)}")
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            error_response = {
                "content": [{
                    "type": "text",
                    "text": f"Error: {str(e)}"
                }],
                "isError": True
            }
            self.wfile.write(json.dumps(error_response).encode())
    
    def call_mcp_tool(self, tool_name, arguments):
        """Call MCP tool via JSON-RPC over stdio"""
        if not self.mcp_process:
            raise Exception("MCP server process is not initialized")
        
        if self.mcp_process.poll() is not None:
            raise Exception(f"MCP server process has terminated (exit code: {self.mcp_process.returncode})")
        
        # Build JSON-RPC request
        request_id = self.request_id
        self.request_id += 1
        
        request = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments
            }
        }
        
        try:
            # Send request to MCP server
            request_json = json.dumps(request) + "\n"
            self.mcp_process.stdin.write(request_json.encode('utf-8'))
            self.mcp_process.stdin.flush()
            
            # Read response (with timeout)
            import select
            import sys
            
            # Wait for response (simple readline with timeout simulation)
            response_line = None
            start_time = time.time()
            timeout = 10.0  # 10 second timeout
            
            while time.time() - start_time < timeout:
                # Try to read a line
                if self.mcp_process.stdout.readable():
                    # Use a simple approach: read until newline
                    line_bytes = b""
                    while True:
                        char = self.mcp_process.stdout.read(1)
                        if not char:
                            break
                        line_bytes += char
                        if char == b'\n':
                            break
                    
                    if line_bytes:
                        response_line = line_bytes.decode('utf-8', errors='ignore').strip()
                        break
                
                time.sleep(0.1)
            
            if not response_line:
                raise Exception("Timeout waiting for MCP server response")
            
            print(f"[MCP RAW] {response_line[:200]}...")
            
            # Parse response
            response = json.loads(response_line)
            
            if "error" in response:
                error_info = response["error"]
                error_msg = error_info.get("message", "Unknown error")
                raise Exception(f"MCP error: {error_msg}")
            
            # Extract result content
            result_data = response.get("result", {})
            content = result_data.get("content", [])
            
            # Ensure content is in the right format
            if isinstance(content, str):
                content = [{"type": "text", "text": content}]
            elif not isinstance(content, list):
                # Convert to list if it's a single object
                if isinstance(content, dict):
                    content = [content]
                else:
                    content = [{"type": "text", "text": str(content)}]
            
            # Ensure each content item has the right structure
            formatted_content = []
            for item in content:
                if isinstance(item, str):
                    formatted_content.append({"type": "text", "text": item})
                elif isinstance(item, dict):
                    # Ensure it has type and text
                    formatted_item = {
                        "type": item.get("type", "text"),
                        "text": item.get("text", item.get("content", str(item)))
                    }
                    formatted_content.append(formatted_item)
                else:
                    formatted_content.append({"type": "text", "text": str(item)})
            
            return {
                "content": formatted_content,
                "isError": False
            }
        except json.JSONDecodeError as e:
            raise Exception(f"Invalid JSON response from MCP server: {str(e)}")
        except Exception as e:
            raise Exception(f"Error communicating with MCP server: {str(e)}")

def find_jar_file():
    """Find the CRM MCP Server JAR file (prefer fat JAR)"""
    project_root = os.path.dirname(os.path.abspath(__file__))
    console_agent_dir = os.path.join(project_root, "console-agent")
    libs_dir = os.path.join(console_agent_dir, "build", "libs")
    
    if not os.path.exists(libs_dir):
        return None
    
    # First, look for the CRM-specific fat JAR
    for file in os.listdir(libs_dir):
        if file.endswith(".jar") and "crm-mcp-server" in file:
            return os.path.join(libs_dir, file)
    
    # Fallback to console-agent JAR
    for file in os.listdir(libs_dir):
        if file.endswith(".jar") and "console-agent" in file and not file.endswith("-sources.jar"):
            return os.path.join(libs_dir, file)
    
    return None

def main():
    # Parse port from command line
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8003
    
    # Find project root
    project_root = os.path.dirname(os.path.abspath(__file__))
    console_agent_dir = os.path.join(project_root, "console-agent")
    
    print("=" * 60)
    print("CRM MCP HTTP Bridge Server")
    print("=" * 60)
    
    # Always try to build fat JAR first (it includes all dependencies)
    print("\n[INFO] Building CRM MCP Server fat JAR (includes all dependencies)...")
    print("Running: ./gradlew :console-agent:crmMcpServerJar")
    
    build_result = subprocess.run(
        ["./gradlew", ":console-agent:crmMcpServerJar"],
        cwd=project_root,
        capture_output=True,
        text=True
    )
    
    if build_result.returncode != 0:
        print(f"[WARNING] Fat JAR build failed:")
        print(build_result.stderr[:500])  # Show first 500 chars
        print("\n[INFO] Trying regular build as fallback...")
        # Fallback to regular build
        build_result = subprocess.run(
            ["./gradlew", ":console-agent:build"],
            cwd=project_root,
            capture_output=True,
            text=True
        )
        if build_result.returncode != 0:
            print(f"[ERROR] Regular build also failed:")
            print(build_result.stderr[:500])
            print("\nPlease build manually: ./gradlew :console-agent:crmMcpServerJar")
            sys.exit(1)
    
    # Find JAR file (prefer fat JAR)
    jar_path = find_jar_file()
    if not jar_path:
        print("[ERROR] JAR file not found after build")
        print("Please check console-agent/build/libs/ directory")
        sys.exit(1)
    
    print(f"[INFO] Using JAR: {jar_path}")
    
    # Start MCP server process
    print(f"[INFO] Starting CRM MCP server...")
    
    # Check if it's a fat JAR (crm-mcp-server) or regular JAR
    is_fat_jar = "crm-mcp-server" in jar_path
    
    try:
        # Set working directory to project root so data file can be found
        # The data file is at: console-agent/data/crm_data.json
        # When run from project root, relative path works
        
        if is_fat_jar:
            # Fat JAR includes all dependencies and correct main class
            print(f"[INFO] Using fat JAR: {jar_path}")
            print(f"[INFO] Command: java -jar {jar_path}")
            print(f"[INFO] Working directory: {project_root} (for data file access)")
            mcp_process = subprocess.Popen(
                ["java", "-jar", jar_path],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=project_root,  # Use project root, not console_agent_dir
                bufsize=0  # Unbuffered
            )
        else:
            # Regular JAR - need to specify main class
            print(f"[INFO] Using regular JAR with explicit main class")
            print(f"[INFO] Command: java -cp {jar_path} com.example.aichat.console.mcp.crm.CrmMcpServerMainKt")
            print(f"[WARNING] Regular JAR may not include all dependencies.")
            print(f"[WARNING] Consider building fat JAR: ./gradlew :console-agent:crmMcpServerJar")
            print(f"[INFO] Working directory: {project_root} (for data file access)")
            mcp_process = subprocess.Popen(
                ["java", "-cp", jar_path, "com.example.aichat.console.mcp.crm.CrmMcpServerMainKt"],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=project_root,  # Use project root, not console_agent_dir
                bufsize=0  # Unbuffered
            )
        
        # Give it a moment to start
        time.sleep(1)
        
        if mcp_process.poll() is not None:
            stderr_output = mcp_process.stderr.read().decode('utf-8', errors='ignore')
            print(f"[ERROR] MCP server process exited immediately:")
            print(stderr_output)
            sys.exit(1)
        
        print("[INFO] MCP server process started")
        
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
            init_json = json.dumps(init_request) + "\n"
            mcp_process.stdin.write(init_json.encode('utf-8'))
            mcp_process.stdin.flush()
            
            # Read init response
            init_response_line = mcp_process.stdout.readline().decode('utf-8', errors='ignore').strip()
            if init_response_line:
                init_response = json.loads(init_response_line)
                print(f"[INFO] MCP server initialized: {init_response.get('result', {}).get('protocolVersion', 'unknown')}")
        except Exception as e:
            print(f"[WARNING] Initialize may have failed: {e}")
            print("[INFO] Continuing anyway...")
        
        # Start HTTP server
        server = HTTPServer(
            ('0.0.0.0', port),
            lambda *args, **kwargs: MCPBridgeHandler(*args, mcp_process=mcp_process, **kwargs)
        )
        
        print("=" * 60)
        print(f"[SUCCESS] CRM MCP Bridge Server running on http://0.0.0.0:{port}")
        print(f"[INFO] For Android Emulator: http://10.0.2.2:{port}")
        print(f"[INFO] Health check: http://localhost:{port}/health")
        print(f"[INFO] Tools list: http://localhost:{port}/tools")
        print("=" * 60)
        print("Press Ctrl+C to stop")
        print("=" * 60)
        
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\n[INFO] Stopping server...")
            mcp_process.terminate()
            try:
                mcp_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                mcp_process.kill()
            server.shutdown()
            print("[INFO] Server stopped")
    
    except FileNotFoundError:
        print("[ERROR] Java not found. Please install Java JDK")
        sys.exit(1)
    except Exception as e:
        print(f"[ERROR] Failed to start server: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
