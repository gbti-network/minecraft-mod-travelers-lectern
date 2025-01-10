# 📖 Traveler's Lectern

A Minecraft mod that provides players with a unique lectern that can regenerate its book after a configurable cooldown period. Built with Fabric for Minecraft 1.21.1.

## ✨ Features

- Automatically respawns books in lecterns after a cooldown
- Configurable cooldown and debug logging settings
- Server-side implementation for multiplayer compatibility
- Custom commands for creating, editing, and destroying Traveler's Lecterns
- Admin-protected lecterns with permission-based interaction

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/).
2. Download the latest version of Traveler's Lectern from the [releases page](https://github.com/gbti-network/minecraft-mod-travelers-lectern/releases).
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
- Lectern data is saved in `travelers_lecterns.json` within the configuration folder. This file is automatically managed by the mod and should not be manually edited.

## ⚙️ Commands

When a player with ops permission level 4 (admin) is in the server, they can use the following commands when looking at a lectern with a book on it:

- `/travelers_lectern create [time]`: Create a Traveler's Lectern with a cooldown in seconds (default: 1800 seconds).
- `/travelers_lectern edit [time]`: Edit an existing Traveler's Lectern to update its cooldown.
- `/travelers_lectern destroy`: Remove a Traveler's Lectern.

## 📘 Usage

1. Place a lectern and a book in it.
2. Use the `/travelers_lectern create` command while facing the lectern to make it a Traveler's Lectern.
3. After the cooldown period, the book will automatically regenerate if taken out.
4. Admin players (permission level 4) can edit or destroy Traveler's Lecterns using the respective commands.

## 🛠 Development

This mod was built using:
- Java 21
- Fabric API
- Gson for JSON parsing and serialization

### Project Structure
- `src/main/java`: Contains the main mod logic.
- `src/main/resources`: Includes assets, mixins, and configuration files.

### Key Files
- **`TravelersLectern.java`**: Initializes the mod and handles server events.
- **`FileManager.java`**: Manages reading and saving configuration files and lectern data.
- **`LecternObject.java`**: Represents a Traveler's Lectern with cooldown logic.
- **`LecternBlockMixin.java`**: Modifies vanilla lectern behavior.

## 🤝 Support

### Join the GBTI Network! 🎮 🚀 💻
The GBTI Network is more than just a community - it's a thriving ecosystem of developers, gamers, and creators! Our members enjoy:

- 🎮 Access to our private Minecraft server
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
- ⭐ Rate us on [Modrinth](#)

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

**Note**: For development and contribution guidelines, please see [CONTRIBUTING.md](CONTRIBUTING.md).