# Asteroids

A vector arcade game for Java Swing, built from components in
[CharlieSwires/scramble](https://github.com/CharlieSwires/scramble).
The ship, saucers and rocks are drawn as scalable outlines in Java2D.
The project needs no game assets or external libraries.

## Run in Eclipse

1. Install Eclipse IDE for Java Developers and configure an [OpenJDK](https://openjdk.org/) that it supports. The project targets Java 8 or later.
2. Extract `Asteroids.zip`.
3. In Eclipse, choose **File → Import → General → Existing Projects into Workspace**.
4. Set **Select root directory** to the extracted `Asteroids` folder and click **Finish**.
5. Open `src/Asteroids.java` and select **Run As → Java Application**.
6. Press **Enter** on the title screen.

You can also run the included JAR with `java -jar dist/Asteroids.jar` from the
project folder, double-click `run.bat` on Windows, or use `./run.sh` on
macOS or Linux. To rebuild the JAR after editing, run `build.bat` or
`./build.sh` with a JDK on your PATH.

## Controls

| Key | Action |
| --- | --- |
| Left / Right | Rotate the ship |
| Up | Thrust; release to coast |
| Space | Fire; hold to repeat |
| Right Ctrl | Hold to raise the shield |
| Enter | Start / start again from the high-score table |
| P | Pause / resume |
| H | View the high-score table from the title screen |
| M | Toggle sound |
| Esc | Return to the title screen |

The shield ring protects your ship from rocks, saucers and enemy shots. You
get 10 seconds of shield time per life. Release Right Ctrl to save the charge
left on the HUD. It refills when you respawn, and carries over between waves.

## Rock and saucer behaviour

- Each wave starts with six independently generated rocks with jagged outlines (four segments per base edge),
  directions of travel, speeds and spins. The playfield wraps at all four edges.
- A random zigzag crack splits the outline into two pieces, with the same
  jagged edge on both. The two pieces drift apart
  and spin in opposite directions. Each moves and spins roughly twice as fast
  as its parent, with independent variation. A rock can split four times along
  each branch; a hit on a smallest fragment destroys it.
- The large red saucer is worth **150–500** points. The small gold saucer is
  worth **600–1,200** points. Each chooses a reward at spawn and displays it when
  destroyed. Each flies at a constant speed with occasional changes in heading
  of up to 45° either way.
- Both saucers aim at where the ship will be if it keeps its current velocity.
  The gun solves `|r + v·t| = bulletSpeed·t` for the earliest reachable point
  (including screen wraparound). The bullet then follows that fixed heading.
- Later waves give the original rocks a 5% compounded speed increase, capped at
  2.1× starting speed. Top ten scores are saved in `.asteroids-scores.bin` in
  your home folder, separate from Scramble.

## Sound

Java Sound synthesizes the two-note beat, thrust, player and enemy shots,
rock hits, saucer calls, explosions and game cues. The game looks for a
working system audio output automatically. If you see **SOUND UNAVAILABLE**,
check `asteroids-sound-diagnostics.txt` in the game's working directory.
Press **M** to mute or unmute.

Run `test.bat` or `./test.sh` with a JDK to check split geometry, movement,
aim, restart, rendering and PCM audio output. The mixer test uses a simulated
audio line, so it does not need speakers.
