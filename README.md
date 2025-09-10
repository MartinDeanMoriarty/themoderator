# themoderator

The only moderator you can trust ;)
<br><br>
llm moderator fabric mod for Minecraft
 
## Table of Contents
- [Warning](#warning)
- [Dependencies](#dependencies)
- [Features](#features)
- [Download](#download)
- [Installation](#installation) 
- [Contributing](#contributing)
- [License](#license)

## Warning
> [!WARNING]
> 
> Use it at your own risk!
> <br>In theory the llm can not do any real harm since it is not able to use server commands or write code.
> <br>But our future oppressors may prove me wrong, so please be careful.

## Dependencies
><a href="https://www.minecraft.net/">Minecraft version = 1.21.4</a>
><br><a href="https://fabricmc.net/">Fabric version = 0.119.4+1.21.4</a>
><br>Loader version = 0.17.2
><br><a href="https://www.curseforge.com/minecraft/mc-mods/fabric-api/files/6863303">fabric-api-0.119.4+1.21.4</a>
><br><a href="https://ollama.com/">Ollama</a> or <a href="https://openai.com/">OpenAi API-Key</a>

## Features
The system is based on llm-self-prompting, trigger words and scheduling tasks.

The llm receives feedback on its own actions which enables self-prompting until it deliberately uses a command to interrupt this. 
At this point it "waits" for requests, a scheduling task or just unloads(ollama).
    
Trigger words are used to send player messages directly to the llm which involves a cooldown. 
<br>The scheduling task caches a summary of server messages to send to the llm periodically.  

The moderator aka llm is able to spawn an avatar. This is just a vanilla mob and not a custom entity! 
<br>The avatar is bound to a chunk-loader and has limited abilities (goto, look at ...) to "interact" with the worlds and players as well. Still mostly untested. 

Technical:
<br>By telling the llm to respond with strict json, it is able to use a so called <b><ACTION</b><b>></b> like this:
<br>```{"action": "<ACTION>", "value": "<VALUE>", "value2": "<VALUE2>", "value3": "<VALUE3>"}```
<br>The json gets parsed to extract the <b><ACTION</b><b>></b> and its values.
<br>A simple use case looks like this:
<br>```{"action": "CHAT", "value": "Text"}```
<br>This way the llm can interact with the server and the players efficient and safe.
<br><b><ACTIONs</b><b>></b> the llm can do for now:
<br>"src/main/java/com.nomoneypirate/llm/ModerationDecision.java"

## Download
It should NOT run on public servers!
<br>It is in development and mostly NOT testet and possibly also unstable! 

However, you can download a compiled and !MOSTLY UNTESTED! version here:
<br><a href="https://drive.google.com/file/d/13R8WikinquK_M0yg64NlT4yN8_BodHKW/view">Latest Build</a>
<br>This version may not be up to date!
<br><br>If you are really into development or just testing every Git repo there is, well, just follow Installation.

## Installation
1. Set up development environment:
   <br>https://wiki.fabricmc.net/tutorial:setup
2. Clone the repository and open the main directory as project.
3. Have a look at: 
   <br>src/main/java/com.nomoneypirate/config/ModConfig.java
   <br>This is for run/config/themoderator/config.json
   <br>And:
   <br>src/main/java/com.nomoneypirate/config/LangConfig.java
   <br>This is for run/config/themoderator/themoderator_(lang).json
   <br><br>Be careful with translations!<br><br>  
4. You can run or build the project in ide.

## Contributing
Contributions are welcome! If you think something is missing, incorrect, or could be improved upon, please feel free to open an issue or submit a pull request.

## License
Creative Commons