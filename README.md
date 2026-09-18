# themoderator

> The only moderator you can trust ;)

llm moderator fabric mod for Minecraft

---

## Table of Contents

- [Warning](#warning)
- [Dependencies](#dependencies)
- [Features](#features)
- [Download](#download)
- [Dev-Installation](#dev-installation)
- [Contributing](#contributing)
- [License](#license)

---

## Warning

> [!WARNING]
> It should NOT run on public servers!
>
> It is in development and mostly NOT tested and possibly also unstable!
>
> In theory the llm can not do any real harm since it is not able to use server commands or write code.
>
> But our future leaders may prove me wrong, so please be careful ;)

---

## Dependencies

- [Minecraft](https://www.minecraft.net/) version = 1.21.8
- [Fabric loader](https://fabricmc.net/) 0.17.2 + api-0.133.4+1.21.8
- One llm provider:

  | Provider | Needs | Status |
  |---|---|---|
  | [Ollama](https://ollama.com/) | nothing (runs locally, free) | tested, incl. models without native tool support |
  | [OpenAI](https://openai.com/) | API-Key | **not tested!** |
  | [Google Gemini](https://gemini.google.com) | API-Key (free tier via AI Studio) | tested |
  | [Anthropic (Claude)](https://console.anthropic.com/) | API-Key | **not tested!** |

  Untested just means: nobody has run it against the real API yet, not that it's known broken. Report back if you try one!

---

## Features

- Chat with your favorite llm inside Minecraft.
- The moderator can welcome new players, periodically check summaries or announce automatic server restarts.
  - see: [ModConfig.java](src/main/java/com/nomoneypirate/config/ModConfig.java)
- The llm interacts with the server through a fixed set of **actions**. Where a model supports it, actions are offered as native tool/function calls; otherwise the moderator falls back to a schema-constrained JSON reply, e.g.:
  ```json
  {"action": "KICK", "value": "Playername", "value2": "Reason"}
  ```
  Either way it's the same safe, fixed action set - the llm still can't run arbitrary server commands or write code.
  - see: [ActionRegistry.java](src/main/java/com/nomoneypirate/llm/tools/ActionRegistry.java) for the full list of actions, [ModerationDecision.java](src/main/java/com/nomoneypirate/llm/ModerationDecision.java) for how a decision is represented internally.
- Conversation history is kept as real chat turns (user/assistant/tool) instead of a hand-rolled text format, so it maps cleanly onto each provider's native chat API and plays nicer with smaller/local models too.

---

## Download

> You can download a compiled and !MOSTLY UNTESTED! version here:

[Latest Build](https://drive.google.com/file/d/13R8WikinquK_M0yg64NlT4yN8_BodHKW/view)

This version may not be up to date! If you are really into development or just testing every Git repo there is, well, just follow [Dev-Installation](#dev-installation).

---

## Dev-Installation

1. Set up development environment: <https://wiki.fabricmc.net/tutorial:setup>
2. Clone the repository and open the main directory as project.
3. Have a look at:
   - [ModConfig.java](src/main/java/com/nomoneypirate/config/ModConfig.java)
   - [LangConfig.java](src/main/java/com/nomoneypirate/config/LangConfig.java) - be careful with translations!
4. Build the project with gradle or run it in the IDE.

---

## Contributing

At the moment it's just core elements. But if you wish to help, contributions are welcome!

---

## License

Creative Commons
