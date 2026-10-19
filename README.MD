# 📖 Traveler's Lectern

A Minecraft mod that provides players with a unique lectern that can regenerate its book after a configurable cooldown period. Built with Fabric for Minecraft 1.20 to 1.20.4, 1.21 to 1.21.11 and 26.1 to 26.3, in one jar.

## ✨ Features

- Automatically respawns books in lecterns after a cooldown
- Configurable cooldown and debug logging settings
- Server-side implementation for multiplayer compatibility: players need no mod
- An in-game lectern manager on Minecraft 1.21.6 and later: `/lecterns`
- Custom commands for creating, editing, and destroying Traveler's Lecterns
- Admin-protected lecterns with permission-based interaction

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/).
2. Download the latest version of Traveler's Lectern from the [releases page](https://github.com/gbti-network/minecraft-mod-travelers-lectern/releases). One jar covers every supported Minecraft version.
3. Place the `.jar` file in your server's `mods` folder.
4. Install the required dependencies:
   - [Fabric API](https://modrinth.com/mod/fabric-api)

## 🔧 Configuration

The mod can be configured through files in the `config/travelers-lectern/` directory:

### Basic Settings (`travelers_lectern_config.txt`)
```properties
debug_logging=false
```

- `debug_logging`: Enable or disable debug logging for detailed information in server logs.

### Lectern Data
- Lectern data is saved in `lecterns.json` within the configuration folder. This file is automatically managed by the mod and should not be manually edited.
- Releases before 2.0.0 saved it in `travelers_lecterns.json`. 2.0.0 imports that file once and leaves it as it was, so going back to an older release still finds your lecterns.
- If `lecterns.json` cannot be read, the mod saves nothing until it can, and says so, rather than writing over it. Its previous version is kept beside it as `lecterns.json.bak`.

## ⚙️ Commands

When a player with ops permission level 4 (admin) is in the server, they can use the following commands when looking at a lectern with a book on it, up to 20 blocks away:

- `/lecterns`: Open the lectern manager (Minecraft 1.21.6 and later).
- `/travelers_lectern create [time]`: Create a Traveler's Lectern with a cooldown in seconds (default: 1800 seconds).
- `/travelers_lectern edit [time]`: Edit an existing Traveler's Lectern to update its cooldown.
- `/travelers_lectern destroy`: Turn a Traveler's Lectern back into a plain lectern. If its book is out, the book goes back on first.

## 📘 Usage

1. Place a lectern and a book in it.
2. Use `/lecterns` or the `/travelers_lectern create` command while facing the lectern to make it a Traveler's Lectern.
3. Players read the book as usual, and take it by sneaking with an empty hand or with the lectern's Take Book button.
4. The book comes back one cooldown after it was taken. Until then the empty lectern takes no other book and tells players when theirs returns.
5. Admin players (permission level 4) can edit or destroy Traveler's Lecterns using the manager or the respective commands. Only they can break one.

## 🗂 Lectern Manager

On Minecraft 1.21.6 and later, `/lecterns` opens a menu, using the game's own server dialogs, so players and admins need no client mod.

- Facing a lectern that holds a book, it offers to make it a Traveler's Lectern, with the respawn time in minutes.
- Facing a Traveler's Lectern, it opens that lectern's editor: change the respawn time, take a copy of the book, use the book in your hand instead, put a taken book back now, teleport there, or turn it back into a plain lectern.
- Anywhere else, it lists every Traveler's Lectern on the server. Hover one to see where it is and whether its book is out.

On older versions, use the `/travelers_lectern` commands above.

## 🛠 Development

This mod was built using:
- Java 17 (Minecraft 1.20.x), Java 21 (1.21.x) and Java 25 (26.x), from one source tree with [Stonecutter](https://stonecutter.kikugie.dev/)
- Fabric API
- Gson for JSON parsing and serialization

### Project Structure
- `src/main/java`: Contains the main mod logic.
- `src/main/resources`: Includes assets, mixins, and configuration files.

### Key Files
- **`TravelersLectern.java`**: Initializes the mod and handles server events.
- **`Lecterns.java`**: Taking the book, the respawn tick, and what the commands and the manager do.
- **`LecternStore.java`**: Reads and saves lectern data, and imports the file older releases left.
- **`LecternBooks.java`**: A lectern's book as text and back, on every supported version.
- **`LecternCommands.java`**: The `/travelers_lectern` and `/lecterns` commands.
- **`LecternBlockMixin.java`**: Hands clicks on a Traveler's Lectern to the mod; other lecterns stay vanilla.
- **`dialog/`**: The lectern manager (1.21.6 and later).

## 🤝 Support

### Join the GBTI Network! 🎮 🚀 💻
The GBTI Network is more than just a community: it's a thriving ecosystem of developers, gamers, and creators! Our members enjoy:

- 💻 Exclusive development tools and resources
- 📚 Private repositories and code libraries
- 👥 Active Discord community for collaboration
- 🌟 Member directory listing to showcase your work
- 🤝 Co-op opportunities and project partnerships
- 🎯 Early access to new features and tools

Support our work by becoming a [GBTI Network member](https://gbti.network/membership/).

### Other Ways to Support:
- 🌟 Star us on [GitHub](https://github.com/gbti-network/minecraft-mod-travelers-lectern)
- 🐛 Report issues on our [Issue Tracker](https://github.com/gbti-network/minecraft-mod-travelers-lectern/issues)
- 💡 Contribute through [Pull Requests](https://github.com/gbti-network/minecraft-mod-travelers-lectern/pulls)
- ❤️ Heart us on [Modrinth](https://modrinth.com/mod/travelers-lectern)

## 📄 License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.

---

**Note**: For development and contribution guidelines, please see [CONTRIBUTING.md](CONTRIBUTING.md).