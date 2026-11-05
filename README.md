<div align="center">

# REST4Models

**Dynamic REST APIs and MCP tools for model management**

Model-Driven Engineering · Eclipse EMF/Ecore · XMI · Spring Boot · REST · MCP

[Overview](#overview) · [Capabilities](#capabilities) · [Quick Start](#quick-start) · [REST APIs](#rest-apis) · [MCP Server](#mcp-server) · [Examples](#examples) 

</div>

## Overview

**REST4Models** is a Model-Driven Engineering (MDE) tool that dynamically exposes Eclipse EMF/Ecore metamodels and XMI models through REST APIs. Leveraging **EMF reflection**, it enables external applications to discover, query, validate, and modify model elements without directly manipulating the underlying files, supporting a **Modeling-as-a-Service** approach.

REST4Models also integrates a **Model Context Protocol (MCP) server** that exposes supported model-management operations as tools for MCP-compatible AI agents and clients. This enables natural-language interaction with models and integration into AI-assisted workflows.


## Capabilities

| Capability | Description |
| --- | --- |
| **Model and metamodel discovery** | Discover registered Ecore packages, classes, and XMI models. |
| **Model and metamodel management** | Create, query, update, and delete supported model elements and Ecore structures. |
| **Repository-wide querying** | Search for model instances across registered models or within a selected XMI file. |
| **Model validation** | Check model conformance and report validation issues. |
| **Model-to-model transformation (M2M)** | Transform source models into target models conforming to supported metamodels, where a transformation is configured. |
| **Model-to-text transformation (M2T)** | Generate supported text artifacts or source code from models, where configured. |
| **Model analysis** | Evaluate supported quality metrics and produce analysis results or reports, when available. |
| **REST and MCP access** | Access model operations over HTTP/JSON or as MCP tools for compatible clients. |

> **Scope note:** The table describes REST4Models capabilities across its development. Not every operation is necessarily exposed in this paper-specific release. Use the running REST API documentation and the MCP client's discovered tools to determine what the checked-out version actually supports.

## Quick Start

### Prerequisites

- **Java 17** (or the version configured in `pom.xml`).
- **Maven**, or the included Maven Wrapper (`mvnw` / `mvnw.cmd`).
- A directory with `.ecore` and `.xmi` files. Use public or synthetic sample models for experiments.

### 1. Clone and build

```bash
git clone <REPOSITORY_URL>
cd REST4Models-MCP
```

Build on Linux/macOS:

```bash
./mvnw clean package
```

Or on Windows PowerShell:

```powershell
.\mvnw.cmd clean package
```

### 2. Configure the model repository

By default, REST4Models scans:

```text
src/main/resources/repository
```

The default is a **relative filesystem path** resolved from the application's working directory. When launching from the project root and using that folder, no additional environment configuration is needed.

Alternatively, set `REPOSITORY_DIR` to an absolute path containing your `.ecore` and `.xmi` files.

Linux/macOS:

```bash
export REPOSITORY_DIR="/absolute/path/to/repository"
```

Windows PowerShell:

```powershell
$env:REPOSITORY_DIR = "C:\path\to\repository"
```

A custom repository is optional. If you have previously set `REPOSITORY_DIR`, its value takes precedence over the default.

### 3. Run the REST application

From the project root:

```bash
./mvnw spring-boot:run
```

On Windows, use `./mvnw.cmd spring-boot:run` or `.\mvnw.cmd spring-boot:run` in PowerShell.

The REST application normally runs at `http://localhost:8080`. If Swagger UI is enabled in the selected profile, open:

**http://localhost:8080/swagger-ui/index.html**

## REST APIs

REST4Models provides endpoints for discovering and managing EMF/Ecore metamodels and XMI models.

For example, to list the registered metamodel packages:

```bash
curl http://localhost:8080/api/v1/metamodels
```

Available endpoints and their request parameters are documented by the running application's OpenAPI/Swagger interface, when enabled.

## MCP Server

REST4Models includes an MCP server for clients that support the **Model Context Protocol**. The STDIO mode allows a compatible client to launch REST4Models as a local process and discover its available model-management tools.

### Build and configure STDIO

Build the application as described above, then identify the generated JAR in `target/`.

The following example shows the structure of a typical MCP client configuration. **Replace the JAR path and filename with the actual build output.**

```json
{
  "mcpServers": {
    "rest4models": {
      "command": "java",
      "args": [
        "-Dspring.main.banner-mode=off",
        "-jar",
        "/absolute/path/to/REST4Models-MCP.jar",
        "--spring.profiles.active=mcp-stdio"
      ],
      "env": {
        "REPOSITORY_DIR": "/absolute/path/to/repository"
      }
    }
  }
}
```

The `env` entry makes the repository location explicit when the MCP client launches the application. If omitted, the default relative path will be resolved from the MCP server process's working directory, which might not be the project root.

**STDIO requirement:** Protocol messages use standard output. Ordinary application logs and startup messages must not be written to standard output, or they may interfere with MCP communication.

Restart or reconnect the MCP client after changing its configuration.

### Available MCP operations

Examples of operations exposed by REST4Models builds include:

| Category | Example tool or operation |
| --- | --- |
| Discover metamodels | `list_packages_with_models` |
| Read model content | `read_model_content`|
| List model instances | `list_instances` / `get_instances_in_model` |
| Create a metamodel class | `create_class` |
| Add an attribute | `add_attribute` |
| Model transformation | `m2m_sysml_model_to_json_model`|
| Model analysis | `model_analysis` |

> 
## Examples

Once an MCP-compatible assistant is connected, try requests such as:

- “Show me the metamodels and models available in the repository.”
- “Transform `VehicleDefinitions.xmi` into a JSON model”