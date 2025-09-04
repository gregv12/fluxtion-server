/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import Ajv from "ajv";
import fetch from "node-fetch";
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "fs";
import { join } from "path";

// Simple file-backed persistence
const DATA_DIR = join(process.cwd(), "data");
const CATALOG_FILE = join(DATA_DIR, "catalog.json");

function ensureDataDir() {
  if (!existsSync(DATA_DIR)) mkdirSync(DATA_DIR, { recursive: true });
  if (!existsSync(CATALOG_FILE)) writeFileSync(CATALOG_FILE, JSON.stringify({ plugins: {} }, null, 2));
}

function loadCatalog(): { plugins: Record<string, any> } {
  ensureDataDir();
  const raw = readFileSync(CATALOG_FILE, "utf-8");
  return JSON.parse(raw);
}

function saveCatalog(catalog: { plugins: Record<string, any> }) {
  ensureDataDir();
  writeFileSync(CATALOG_FILE, JSON.stringify(catalog, null, 2));
}

// Load schema and init Ajv
const manifestSchema = JSON.parse(
  readFileSync(new URL("../schema/plugin-manifest-v1.json", import.meta.url)).toString()
);
const ajv = new Ajv({ allErrors: true, allowUnionTypes: true });
const validateManifest = ajv.compile(manifestSchema);

const server = new Server({ name: "fluxtion-plugin-catalog", version: "0.1.0" });
const mcp = server as any;

// Utilities
function nowIso() {
  return new Date().toISOString();
}

function summarize(manifest: any) {
  return {
    pluginId: manifest.pluginId,
    name: manifest.name,
    version: manifest.version,
    description: manifest.description,
    keywords: manifest.keywords || []
  };
}

function capabilityIndex(pluginId: string, manifest: any) {
  return (manifest.capabilities || []).map((c: any) => ({
    pluginId,
    capabilityId: c.id,
    title: c.title,
    summary: c.summary || ""
  }));
}

// Tools
mcp.tool("register_plugin", async (input: any) => {
  const { pluginId: pluginIdOverride, manifest, manifestUrl, replace } = input || {};
  let manifestObj: any = manifest || null;

  if (!manifestObj && manifestUrl) {
    if (typeof manifestUrl !== "string" || !manifestUrl.startsWith("https://")) {
      throw new Error("manifestUrl must be an https URL");
    }
    const res = await fetch(manifestUrl);
    if (!res.ok) throw new Error(`Failed to fetch manifest: ${res.status}`);
    manifestObj = await res.json();
  }

  if (!manifestObj) throw new Error("Provide manifest or manifestUrl");

  const valid = validateManifest(manifestObj);
  if (!valid) {
    return { status: "invalid", errors: validateManifest.errors };
  }

  const pluginId = pluginIdOverride || manifestObj.pluginId;
  const catalog = loadCatalog();
  const exists = !!catalog.plugins[pluginId];

  if (exists && !replace) {
    return { status: "exists", pluginId, version: catalog.plugins[pluginId].version };
  }

  const withMeta = {
    ...manifestObj,
    _registeredAt: exists ? catalog.plugins[pluginId]._registeredAt : nowIso(),
    _updatedAt: nowIso()
  };
  catalog.plugins[pluginId] = withMeta;
  saveCatalog(catalog);

  return { status: exists ? "updated" : "created", pluginId, version: withMeta.version };
});

mcp.tool("update_plugin", async (input: any) => {
  const { pluginId, patch } = input || {};
  if (!pluginId) throw new Error("pluginId required");
  const catalog = loadCatalog();
  const curr = catalog.plugins[pluginId];
  if (!curr) throw new Error("plugin not found");
  const updated = { ...curr, ...patch, _updatedAt: nowIso() };
  // Re-validate after patch
  const valid = validateManifest(updated);
  if (!valid) return { status: "invalid", errors: validateManifest.errors };
  catalog.plugins[pluginId] = updated;
  saveCatalog(catalog);
  return { status: "updated", pluginId };
});

mcp.tool("list_plugins", async (input: any) => {
  const { tag, q } = input || {};
  const catalog = loadCatalog();
  const list = Object.values(catalog.plugins).map(summarize);
  const filtered = list.filter((p: any) => {
    const tagOk = tag ? (p.keywords || []).includes(tag) : true;
    const qOk = q
      ? (p.name + " " + (p.description || "") + " " + (p.keywords || []).join(" ")).toLowerCase().includes(q.toLowerCase())
      : true;
    return tagOk && qOk;
  });
  return filtered;
});

mcp.tool("get_plugin", async (input: any) => {
  const { pluginId } = input || {};
  if (!pluginId) throw new Error("pluginId required");
  const catalog = loadCatalog();
  const manifest = catalog.plugins[pluginId];
  if (!manifest) throw new Error("plugin not found");
  return { manifest };
});

mcp.tool("search_capabilities", async (input: any) => {
  const { terms } = input || {};
  const words: string[] = Array.isArray(terms) ? terms.map((t) => String(t).toLowerCase()) : [];
  const catalog = loadCatalog();
  const results: any[] = [];
  for (const [pid, m] of Object.entries(catalog.plugins)) {
    for (const c of m.capabilities || []) {
      const hay = [c.id, c.title, c.summary, ...(c.tags || [])].join(" ").toLowerCase();
      const score = words.reduce((acc, w) => acc + (hay.includes(w) ? 1 : 0), 0);
      if (score > 0 || words.length === 0) {
        results.push({ pluginId: pid, capabilityId: c.id, title: c.title, summary: c.summary || "", score });
      }
    }
  }
  results.sort((a, b) => b.score - a.score);
  return results;
});

mcp.tool("add_repository", async (input: any) => {
  const { pluginId, repository } = input || {};
  if (!pluginId || !repository) throw new Error("pluginId and repository required");
  const catalog = loadCatalog();
  const m = catalog.plugins[pluginId];
  if (!m) throw new Error("plugin not found");
  m.repositories = m.repositories || [];
  m.repositories.push(repository);
  m._updatedAt = nowIso();
  const valid = validateManifest(m);
  if (!valid) return { status: "invalid", errors: validateManifest.errors };
  saveCatalog(catalog);
  return { status: "updated", pluginId };
});

mcp.tool("add_documentation", async (input: any) => {
  const { pluginId, documentation } = input || {};
  if (!pluginId || !documentation) throw new Error("pluginId and documentation required");
  const catalog = loadCatalog();
  const m = catalog.plugins[pluginId];
  if (!m) throw new Error("plugin not found");
  m.documentation = m.documentation || [];
  m.documentation.push(documentation);
  m._updatedAt = nowIso();
  const valid = validateManifest(m);
  if (!valid) return { status: "invalid", errors: validateManifest.errors };
  saveCatalog(catalog);
  return { status: "updated", pluginId };
});

mcp.tool("add_example", async (input: any) => {
  const { pluginId, example } = input || {};
  if (!pluginId || !example) throw new Error("pluginId and example required");
  const catalog = loadCatalog();
  const m = catalog.plugins[pluginId];
  if (!m) throw new Error("plugin not found");
  m.examples = m.examples || [];
  m.examples.push(example);
  m._updatedAt = nowIso();
  const valid = validateManifest(m);
  if (!valid) return { status: "invalid", errors: validateManifest.errors };
  saveCatalog(catalog);
  return { status: "updated", pluginId };
});

mcp.tool("export_catalog", async () => {
  const catalog = loadCatalog();
  return { catalog };
});

mcp.tool("validate_manifest", async (input: any) => {
  const { manifest } = input || {};
  if (!manifest) throw new Error("manifest required");
  const valid = validateManifest(manifest);
  return { valid, errors: validateManifest.errors || [] };
});

// Resources
mcp.resource({
  uri: "mcp://fluxtion-plugins/catalog",
  get: async () => {
    const catalog = loadCatalog();
    const list = Object.values(catalog.plugins).map((m: any) => summarize(m));
    return { mimeType: "application/json", data: JSON.stringify(list, null, 2) };
  }
});

mcp.resource({
  uri: /mcp:\/\/fluxtion-plugins\/plugin\/([^\/@]+)(?:@([^\/]+))?$/,
  get: async (_ctx: any, match: any) => {
    const pluginId = match?.[1];
    if (!pluginId) throw new Error("pluginId missing");
    const catalog = loadCatalog();
    const m = catalog.plugins[pluginId];
    if (!m) throw new Error("plugin not found");
    return { mimeType: "application/json", data: JSON.stringify(m, null, 2) };
  }
});

mcp.resource({
  uri: /mcp:\/\/fluxtion-plugins\/plugin\/([^\/]+)\/capabilities$/,
  get: async (_ctx: any, match: any) => {
    const pluginId = match?.[1];
    const catalog = loadCatalog();
    const m = catalog.plugins[pluginId];
    if (!m) throw new Error("plugin not found");
    const caps = capabilityIndex(pluginId!, m);
    return { mimeType: "application/json", data: JSON.stringify(caps, null, 2) };
  }
});

mcp.resource({
  uri: /mcp:\/\/fluxtion-plugins\/plugin\/([^\/]+)\/develop$/,
  get: async (_ctx: any, match: any) => {
    const pluginId = match?.[1];
    const catalog = loadCatalog();
    const m = catalog.plugins[pluginId];
    if (!m) throw new Error("plugin not found");
    const lines: string[] = [];
    lines.push(`# Develop with ${m.name} (${pluginId})`);
    if (m.description) lines.push(m.description);
    if (m.development?.build) lines.push(`Build: ${m.development.build}`);
    if (m.development?.run) lines.push(`Run: ${m.development.run}`);
    if (m.development?.test) lines.push(`Test: ${m.development.test}`);
    if (Array.isArray(m.capabilities)) {
      lines.push("Capabilities:");
      for (const c of m.capabilities) {
        lines.push(`- ${c.id}: ${c.title}${c.summary ? " - " + c.summary : ""}`);
      }
    }
    if (Array.isArray(m.documentation) && m.documentation.length) {
      lines.push("Docs:");
      for (const d of m.documentation) lines.push(`- [${d.title}](${d.url})${d.category ? ` (${d.category})` : ""}`);
    }
    if (Array.isArray(m.examples) && m.examples.length) {
      lines.push("Examples:");
      for (const e of m.examples) lines.push(`- ${e.title}${e.repo ? ` (${e.repo}${e.path ? "/" + e.path : ""})` : ""}`);
    }
    return { mimeType: "text/markdown", data: lines.join("\n") };
  }
});

// Prompts
mcp.prompt("develop_with_plugin", async ({ pluginId, goal }: any) => {
  const catalog = loadCatalog();
  const m = catalog.plugins[pluginId];
  if (!m) throw new Error("plugin not found");
  const guide = `Goal: ${goal || "Develop with plugin"}\n\n` +
    `Plugin: ${m.name} (${pluginId}) v${m.version}\n` +
    `${m.description || ""}\n\n` +
    `Build: ${m.development?.build || "n/a"}\nRun: ${m.development?.run || "n/a"}\nTest: ${m.development?.test || "n/a"}\n\n` +
    `Capabilities:\n` +
    (m.capabilities || []).map((c: any) => `- ${c.id} (${c.title})`).join("\n") +
    `\n\nDocs:\n` +
    (m.documentation || []).map((d: any) => `- ${d.title}: ${d.url}`).join("\n") +
    `\n\nExamples:\n` +
    (m.examples || []).map((e: any) => `- ${e.title}${e.commands ? ` commands: ${e.commands.join(" | ")}` : ""}`).join("\n");
  return { instructions: guide };
});

mcp.prompt("create_new_plugin_skeleton", async ({ name, pluginId }: any) => {
  const template = {
    pluginId,
    name,
    version: "0.0.1",
    description: "Describe your plugin",
    fluxtion: { minVersion: "", testedVersions: [] },
    capabilities: [],
    repositories: [],
    documentation: [],
    examples: [],
    development: { build: "mvn -q -DskipTests package", run: "", test: "mvn -q test", configuration: "" },
    license: "Apache-2.0",
    maintainers: [],
    keywords: []
  };
  const steps = [
    "1) Copy the manifest template below to fluxtion-plugin.json at your repo root",
    "2) Fill out capabilities, repositories, docs, and examples",
    "3) Build and publish your repo",
    "4) Register with the catalog using register_plugin (manifestUrl or manifest payload)"
  ].join("\n");
  return { steps, manifestTemplate: template };
});

mcp.prompt("troubleshoot_plugin", async ({ pluginId }: any) => {
  const catalog = loadCatalog();
  const m = catalog.plugins[pluginId];
  if (!m) throw new Error("plugin not found");
  const checks = [
    "- Validate manifest via validate_manifest",
    "- Ensure capability ids are unique",
    "- Provide at least one repository or documentation entry",
    "- Ensure build/test commands are correct",
    "- Confirm docs URLs are reachable (HTTPS)"
  ].join("\n");
  return { checklist: checks };
});

mcp.start();
console.log("[fluxtion-plugin-catalog] MCP server started");
