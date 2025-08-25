# themoderator

The only moderator you can trust ;)

LLM moderator fabric mod for Minecraft 1.21.4 

Dependencies:
<br>-fabric-api-0.119.4+1.21.4
<br>-Ollama
 
## Table of Contents
- [Warning](#warning)
- [Features](#features)
- [Download](#download)
- [Installation](#installation)
- [Contributing](#contributing)
- [License](#license)

## Warning
Use it at your own risk!
<br>In theory the llm can not do any real harm since it is not able to use server commands or write code.
<br>But our future oppressors may prove me wrong, so please be careful.

## Features
By forcing the llm to respond with strict json, it is able to use a so called <b><ACTION</b><b>></b> like this:
<br>{"action": "<b><ACTION</b><b>></b>", "value": "<b><VALUE</b><b>></b>", "value2": "<b><VALUE2</b><b>></b>"}.
<br>The json gets parsed to extract the <b><ACTION</b><b>></b> and its values.
A simple use case looks like this: <br>{"action": "CHAT", "value": "<b><Text</b><b>></b>"}.
<br>This way the llm can interact with the server and the players efficient and safe.
<br><b><ACTION</b><b>></b> the llm can do for now:
<br>"src/main/java/com.nomoneypirate/llm/ModerationDecision.java"

## Download
For now, I do not feel like it is a thing and at this point it should not be used on public servers!
<br>However, as soon as important and necessary <b><ACTIONS</b><b>></b> are added and there is actually something to show, there will be (probably) a release.
<b>If you are really into development or just testing every Git repo there is, well, just follow Installation. 

## Installation
1. Set up development environment:
   <br>https://wiki.fabricmc.net/tutorial:setup
2. Clone the repository and open the main directory as project.
3. Edit the config file and put in the model name!
      <br>For example: "gemma3:latest"
4. You can Run the project in ide. 
5. Or build the project with Gradle.
6. Make sure Ollama ist installed on your system!

## Contributing
Contributions are welcome! If you think something is missing, incorrect, or could be improved upon, please feel free to open an issue or submit a pull request.

## License
Creative Commons