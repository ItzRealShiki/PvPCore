# AIAdmin — standalone Paper AI administrator

AIAdmin is a separate Paper plugin JAR and does not depend on PvPCore.

## Requirements

- Paper 26.2
- Java 25
- OpenAI API key (API usage is billed separately from a ChatGPT subscription)
- Outbound HTTPS access to api.openai.com

## Install

1. Stop the server and upload AIAdmin-1.0.0.jar into the server's plugins folder.
2. Start once to create plugins/AIAdmin/config.yml.
3. Enter your OpenAI API key under openai.api-key, or set OPENAI_API_KEY in the server environment.
4. Restart and run /aiadmin status.

Never publish your API key or commit a populated config file to GitHub.

## Commands

- /aiadmin status — server, Java, memory and API status.
- /aiadmin analyze [question] — diagnose recent logs, crash report and server/plugin facts.
- /aiadmin ask <question> — ask a server-related question with diagnostic context.
- /aiadmin logs — show a short sanitized tail of logs/latest.log.
- /aiadmin proposals — list pending repair proposals.
- /aiadmin approve <id> — approve and apply a supported proposal.
- /aiadmin reject <id> — reject a proposal.
- /aiadmin monitor <on|off> — toggle automatic log monitoring.
- /aiadmin reload — reload configuration.

All commands require aiadmin.admin (default: operators). Console use is also supported.

## Features

- Reads the latest server log and newest crash report.
- Collects server version, Java version, online count, memory usage and installed plugin names/versions.
- Watches newly appended error/exception log lines and requests an AI diagnosis subject to a cooldown.
- Stores repair proposals persistently.
- After explicit approval, can change an existing scalar setting in plugins/<PluginName>/config.yml (with timestamped backup) or run a narrow allowlist of safe commands (save-all, difficulty, weather, time and selected boolean gamerules).
- For code changes, plugin replacements, restarts or unsupported commands, it provides manual instructions only.

AIAdmin never downloads/replaces plugin JARs, executes arbitrary shell/console commands, edits server.properties, grants permissions or restarts the host. Every supported change requires /aiadmin approve <id>.

## Data sent to OpenAI

On manual analysis or automatic error alert, AIAdmin sends server versions, player count (not player names), memory figures, plugin names/versions, a limited excerpt of logs/latest.log and optionally the latest crash-report excerpt. Common API-key/password/token patterns and IPv4 addresses are redacted, and chat lines are removed. Redaction is best-effort; disable privacy.include-logs or privacy.include-crash-report to avoid sending those contents.

Requests go directly from the plugin to the official OpenAI Responses API over HTTPS. There is no dashboard or companion service. API calls may incur charges on your API account.

## Build

Run mvn --batch-mode clean package from this directory. Output: target/AIAdmin-1.0.0.jar.
