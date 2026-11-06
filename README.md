<div align="center">

# REST4Models

**Dynamic REST APIs and MCP tools for model management**

Model-Driven Engineering · Eclipse EMF/Ecore · XMI · Spring Boot · REST · MCP

[Overview](#overview) · [Capabilities](#capabilities) · [Quick Start](#quick-start) · [REST APIs](#rest-apis) · [MCP Server](#mcp-server) · [Example](#example-model-discovery) 

</div>

## Overview

**REST4Models** is a Model-Driven Engineering (MDE) tool that dynamically provisions REST APIs for model management. It exposes metamodels and models through RESTful services, enabling external applications to discover, query, validate, and modify model elements.

REST4Models also integrates a **Model Context Protocol (MCP) server** that exposes model-management operations as tools to AI assistants, enabling users to interact with and manage models through natural-language requests.

## Capabilities

| Capability | Description |
| --- | --- |
| **Model and metamodel discovery** | Discover registered metamodels (.ecore), their structural elements, and associated XMI models (.xmi). |
| **Model and metamodel management** | Create, query, update, and delete model elements and metamodel structures. |
| **Repository-wide querying** | Search for model instances across registered models or within a specific model. |
| **Model validation** | Check model conformance and report validation issues. |
| **Model-to-model transformation (M2M)** | Transform source models into target models conforming to the same or different metamodels. |
| **Model-to-text transformation (M2T)** | Generate text artifacts or source code from models. |
| **Model analysis** | Evaluate models using quality metrics and generate analysis reports. |
| **REST and MCP access** | Expose model-management operations through REST APIs and MCP tools, enabling integration with external applications and AI agents. |

## Quick Start

### Prerequisites

- **Java 17 or later**.
- **Git** (to clone the GitHub repository).

The project includes the Maven Wrapper, so a separate Maven installation is not required.

> Note: REST4Models includes sample metamodels (.ecore) and models (.xmi), so no additional model repository configuration is required when running the application from the project root.

### Run the Application

**1. Clone the GitHub repository**

Clone the repository and navigate to the project directory:

```bash
git clone https://github.com/AdielTuyishime/REST4Models-MCP.git
cd REST4Models-MCP
```

**2. Start the application**

Use the included Maven Wrapper to start REST4Models.

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
./mvnw spring-boot:run
```

**3. Access the application**

Once the application starts, the REST APIs are available at:

- **Base URL:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui/index.html

By default, REST4Models automatically discovers Ecore metamodels (`.ecore`) and XMI models (`.xmi`) from `src/main/resources/repository`.

## REST APIs

REST4Models provides REST endpoints for discovering and managing EMF/Ecore metamodels and XMI models.

For example, to list the registered metamodels and their associated models:

**Using a web browser:**

[http://localhost:8080/api/v1/repository/list](http://localhost:8080/api/v1/repository/list)

**Using the command line:**

```bash
curl http://localhost:8080/api/v1/repository/list
```

For the complete list of available endpoints and their parameters, refer to the [Swagger UI](http://localhost:8080/swagger-ui/index.html).

> **Note:** The endpoint URLs are accessible when REST4Models is running locally on port `8080`.


## MCP Server

REST4Models integrates a **Model Context Protocol (MCP) server** that exposes model-management operations as tools to MCP-compatible AI assistants and clients, enabling natural-language interaction with metamodels and models.

### 1. Build the MCP Server

Generate the executable JAR using the included Maven Wrapper.

**Windows (PowerShell):**

```powershell
.\mvnw.cmd clean package
```

**Linux / macOS:**

```bash
./mvnw clean package
```

The generated JAR is located in the `target/` directory.

### 2. Configure an MCP Client

REST4Models supports MCP integration through the **STDIO transport**, allowing compatible AI assistants and development environments to launch the server and invoke its tools.

The configuration format depends on the MCP client.

#### Example: Claude Desktop (STDIO)

Add the following configuration to your Claude Desktop MCP configuration file:

```json
{
  "mcpServers": {
    "rest4models": {
      "command": "java",
      "args": [
        "-Dspring.main.banner-mode=off",
        "-jar",
        "/absolute/path/to/REST4Models1.0-0.11.0.jar",
        "--spring.profiles.active=mcp-stdio"
      ],
      "env": {
        "REPOSITORY_DIR": "/absolute/path/to/repository"
      }
    }
  }
}
```
Replace the JAR and model repository "REPOSITORY_DIR" paths with their absolute locations on your machine. The `REPOSITORY_DIR` variable points to the directory containing the Ecore metamodels and XMI models.

> **Note:** Other MCP-compatible clients may require different configuration formats. Refer to the documentation of your preferred client for instructions on connecting a local STDIO MCP server.

### 3. Connect and Interact

Restart or reconnect the MCP client after saving the configuration. Once connected, the client can discover the available REST4Models tools.


### Example: Model Discovery

Once REST4Models is connected to Claude Desktop via MCP, users can discover the metamodels and models available in the configured model repository through natural-language requests.

**Example prompt:**

> List all the registered metamodels and their associated models in the repository.

Claude Desktop invokes the appropriate MCP tool to retrieve the registered Ecore metamodels and their associated XMI models.

> **Note:** The returned metamodels and models depend on the contents of the configured model repository.
