# themoderator

The only moderator you can trust ;)
<br><br>
llm moderator fabric mod for Minecraft
 ---
## Table of Contents

>- [Warning](#warning)
>- [Dependencies](#dependencies)
>- [Features](#features)
>- [Download](#download)
>- [Dev-Installation](#dev-installation) 
>- [Contributing](#contributing)
>- [License](#license)

---

## Warning

> [!WARNING]
> <br>It should NOT run on public servers!
> <br><br>It is in development and mostly NOT tested and possibly also unstable!
> <br><br>In theory the llm can not do any real harm since it is not able to use server commands or write code.
> <br><br>But our future leaders may prove me wrong, so please be careful.
---
## Dependencies
><a href="https://www.minecraft.net/">Minecraft version = 1.21.8</a>
><br><a href="https://fabricmc.net/">Fabric loader 0.17.2 + api-0.133.4+1.21.8</a>
><br><a href="https://ollama.com/">Ollama</a> or <a href="https://openai.com/">OpenAi API-Key</a> or <a href="https://gemini.google.com">Google Gemini API-Key</a>

---
## Features

>Chat with you favorite llm inside Minecraft.
<br>The moderator can welcome new players, periodically check summaries or announce automatic server restarts.
<br>-see: (src/main/java/com.nomoneypirate/config/ModConfig.java)
<br>The llm can use so called <b>actions</b> like this:
<br>```{"action": "KICK", "value": "Playername"}```
<br>This way the llm can interact safely with the server and the players.
<br><b>Actions</b> the llm can do for now:
<br>-see (src/main/java/com.nomoneypirate/llm/ModerationDecision.java)
---
## Download

>You can download a compiled and !MOSTLY UNTESTED! version here:
<br><a href="https://drive.google.com/file/d/13R8WikinquK_M0yg64NlT4yN8_BodHKW/view">Latest Build</a>
<br>This version may not be up to date!
<br><br>If you are really into development or just testing every Git repo there is, well, just follow Dev-Installation.
---
## Dev-Installation

>1. Set up development environment:
   <br>https://wiki.fabricmc.net/tutorial:setup
>2. Clone the repository and open the main directory as project.
>3. Have a look at: 
   <br>src/main/java/com.nomoneypirate/config/ModConfig.java
   <br>And:
   <br>src/main/java/com.nomoneypirate/config/LangConfig.java
   <br>Be careful with translations!<br>  
>4. Build the project with gradle or run it in the ide.
---
## Contributing

>At the Moment its just core elements.
<br>But if you wish to help, contributions are welcome!
---
## License

>Creative Commons
---