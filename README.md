# MoParticles

Moparticles Is A Minecraft plugin that parses **MoLang** (The Bedrock particle language),
bakes the result into a Java-edition compatible animation (Using Item displays), generates a
resource pack with the necessary textures, and plays the animation
client-side.

## Build

```
./gradlew shadowJar
```
 
## Commands

```
/moparticles list
/moparticles play <effect> <location> [radius]
/moparticles playhere <effect> [radius]
/moparticles stop <id>
/moparticles stopall
/moparticles reload
/moparticles info <effect>
```

## Example particles

I bundled two effects from snowstorm & A custom made one using snowstorm into MoParticles for a showcase. (in `src/main/resources/particles/`):

* `snowstorm:fire`        – Custom snowstorm fire particle
* `snowstorm:loading`     - Snowstorm's Loading circle particle
* `snowstorm:rainbow`     - Snowstorm's Rainbow particle animation

Add your own by dropping more `.json` files into
`plugins/MoParticles/particles/` and running `/moparticles reload`.