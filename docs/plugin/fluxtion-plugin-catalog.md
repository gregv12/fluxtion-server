# Fluxtion Plugin Catalog (MCP Server)

This is a Model Context Protocol (MCP) server that maintains a catalog of Fluxtion plugins and exposes their capabilities to LLM agents. Plugin authors can register manifests describing their plugin’s services, agents, docs, repos, and runnable examples. LLMs and MCP clients can query this catalog to discover “what the plugin is” and “how to develop with it.”

Location in repo: tools/fluxtion-plugin-catalog

## Quick Start

Prerequisites: Node 18+.

1) Install deps
- cd tools/fluxtion-plugin-catalog
- npm install

2) Run in dev (watch) or build+run
- npm run dev
- or
- npm run build && npm start

The server uses a simple JSON file for persistence at tools/fluxtion-plugin-catalog/data/catalog.json.

## Plugin Author Guide

1) Create a manifest at your repo root named fluxtion-plugin.json (JSON or YAML if you convert to JSON before posting). Use this template:

```
{
  "pluginId": "your-plugin-id",
  "name": "Your Plugin Name",
  "version": "0.0.1",
  "description": "Describe your plugin",
  "fluxtion": { "minVersion": "", "testedVersions": [] },
  "capabilities": [
    {
      "id": "feature.id",
      "title": "Feature Title",
      "summary": "What this capability does",
      "tags": ["tag1"],
      "services": ["FullyQualifiedServiceName"],
      "agents": ["FullyQualifiedAgentName"],
      "inputs": ["EventType"],
      "outputs": ["OutputType"]
    }
  ],
  "repositories": [ { "type": "git", "url": "https://github.com/you/your-repo" } ],
  "documentation": [ { "title": "README", "url": "https://github.com/you/your-repo#readme", "category": "readme" } ],
  "examples": [
    {
      "title": "Runnable Example",
      "description": "How to run",
      "repo": "https://github.com/you/your-repo",
      "path": "examples/basic",
      "commands": ["mvn -q -DskipTests package", "java -jar target/app.jar"],
      "expectedOutput": "Server started"
    }
  ],
  "development": {
    "build": "mvn -q -DskipTests package",
    "run": "java -jar target/app.jar",
    "test": "mvn -q test",
    "configuration": "See AppConfig/ServerConfigurator"
  },
  "license": "Apache-2.0",
  "maintainers": [{"name": "Team", "url": "https://your.site"}],
  "keywords": ["fluxtion", "domain-tag"]
}
```

2) Register your plugin with the catalog
- Provide a manifest URL (preferred): call the MCP tool register_plugin with { "manifestUrl": "https://raw.githubusercontent.com/you/your-repo/main/fluxtion-plugin.json" }
- Or send the manifest payload directly: register_plugin with { "manifest": { ... } }

3) Update docs/repositories/examples over time
- Use add_documentation, add_repository, and add_example tools to augment your entry.

4) Validation & quality
- The server validates against schema schema/plugin-manifest-v1.json.
- Use validate_manifest to check before registering.

## LLM Integration Guide (MCP Clients)

The server exposes MCP tools, resources, and prompts. Clients connect per your MCP runtime (e.g., IDE, agent). Key endpoints:

Tools:
- register_plugin({ manifest | manifestUrl, replace? })
- update_plugin({ pluginId, patch })
- list_plugins({ tag?, q? })
- get_plugin({ pluginId })
- search_capabilities({ terms: string[] })
- add_repository, add_documentation, add_example
- export_catalog()
- validate_manifest({ manifest })

Resources:
- mcp://fluxtion-plugins/catalog — JSON summary of all plugins
- mcp://fluxtion-plugins/plugin/{pluginId} — full manifest
- mcp://fluxtion-plugins/plugin/{pluginId}/capabilities — capability index
- mcp://fluxtion-plugins/plugin/{pluginId}/develop — synthesized developer guide (markdown)

Prompts:
- develop_with_plugin({ pluginId, goal? }) — returns instructions string
- create_new_plugin_skeleton({ name, pluginId }) — returns steps + manifest template
- troubleshoot_plugin({ pluginId }) — checklist

### Example Sessions

- Register a plugin from URL
  - tool: register_plugin
  - input: { "manifestUrl": "https://raw.githubusercontent.com/acme/fluxtion-server-plugins/main/serverplugin-trading/fluxtion-plugin.json" }
  - output: { "status": "created", "pluginId": "serverplugin-trading", "version": "1.2.0" }

- List plugins tagged with trading
  - tool: list_plugins
  - input: { "tag": "trading" }

- Get capability index
  - resource: mcp://fluxtion-plugins/plugin/serverplugin-trading/capabilities

- Developer guide
  - resource: mcp://fluxtion-plugins/plugin/serverplugin-trading/develop
  - or prompt: develop_with_plugin({ "pluginId": "serverplugin-trading", "goal": "Implement strategy" })

## Notes
- Persistence is file-backed and suitable for local/dev. For multi-user or production, back it with a DB and add auth.
- Only HTTPS manifest URLs are accepted by default.
- Version this service independently of the Fluxtion server core.
