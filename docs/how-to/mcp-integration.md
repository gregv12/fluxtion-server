# MCP Integration: Fluxtion Plugin Catalog

This guide explains how an LLM/MCP client connects to and uses the Fluxtion Plugin Catalog server in tools/fluxtion-plugin-catalog.

## Start the server
- cd tools/fluxtion-plugin-catalog
- npm install
- npm run dev  (for development)  or  npm run build && npm start

## MCP endpoints

Tools:
- register_plugin({ manifest | manifestUrl, replace? }): Register plugin
- update_plugin({ pluginId, patch }): Update manifest fields
- list_plugins({ tag?, q? }): Search by tag or free text
- get_plugin({ pluginId }): Retrieve full manifest
- search_capabilities({ terms: string[] }): Search across capabilities
- add_repository({ pluginId, repository })
- add_documentation({ pluginId, documentation })
- add_example({ pluginId, example })
- export_catalog()
- validate_manifest({ manifest })

Resources:
- mcp://fluxtion-plugins/catalog
- mcp://fluxtion-plugins/plugin/{pluginId}
- mcp://fluxtion-plugins/plugin/{pluginId}/capabilities
- mcp://fluxtion-plugins/plugin/{pluginId}/develop

Prompts:
- develop_with_plugin({ pluginId, goal? })
- create_new_plugin_skeleton({ name, pluginId })
- troubleshoot_plugin({ pluginId })

## Example client usage

Pseudo-code (Node MCP client):

```ts
import { Client } from "@modelcontextprotocol/sdk/client";

const client = new Client({ /* transport specifics for your environment */});
await client.connect();

// Register by URL
await client.callTool("register_plugin", {
  manifestUrl: "https://raw.githubusercontent.com/acme/fluxtion-server-plugins/main/serverplugin-trading/fluxtion-plugin.json"
});

// Discover
const trading = await client.callTool("list_plugins", { tag: "trading" });
const caps = await client.getResource("mcp://fluxtion-plugins/plugin/serverplugin-trading/capabilities");

// Dev guide via prompt
const guide = await client.callPrompt("develop_with_plugin", { pluginId: "serverplugin-trading", goal: "Build a strategy" });
console.log(guide.instructions);
```

For IDEs/editors that support MCP (e.g., via plugins), configure a connection to this server and use provided tools/resources/prompts directly.
