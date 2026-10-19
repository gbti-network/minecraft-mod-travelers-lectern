# Changelog

## [2.0.0]
- One jar for Minecraft 1.20 to 1.20.4, 1.21 to 1.21.11 and 26.1 to 26.3. Releases before this ran on 1.21.1 only, and the 1.2.x jars crashed on 1.21.2 and later.
- A lectern manager on 1.21.6 and later: `/lecterns` opens the lectern you face, or a list of every Traveler's Lectern with where it is and whether its book is out. From it, set the respawn time in minutes, give yourself a copy, use the book in your hand, put a taken book back now, teleport there, or turn it plain.
- Anyone can take the book, by sneaking with an empty hand or with the lectern's Take Book button, and it comes back one cooldown after it was taken. Reading it no longer restarts the wait.
- An empty Traveler's Lectern tells players when its book returns, in minutes rather than seconds.
- Turning a Traveler's Lectern plain while its book is out puts the book back first.
- Lecterns are saved in `lecterns.json`, keyed by dimension as well as position, so lecterns at the same spot in two dimensions no longer collide. `travelers_lecterns.json` is imported once and left as it was.
- A data file that cannot be read is no longer written over with an empty one.

## [1.2.9]
- Initial release with basic lectern functionality