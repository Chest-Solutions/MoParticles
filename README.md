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

# Using MoParticles in your plugin.

### 1. Build settings
First add the jar to your minecraft plugin.

Gradle
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.Chest-Solutions:MoParticles:1.0.0'
}
```

Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.Chest-Solutions</groupId>
        <artifactId>MoParticles</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### 2. Dependency
Make sure to add MoParticles to your dependencies

plugin.yml
```yaml
name: MyPlugin
version: 1.0
main: com.example.plugin.MyPlugin
depend: [MoParticles] 
```

### 3. Usage

Here's a simple example of playing a particle.
```java
MoParticleAPI api = MoParticleAPI.get();
String effectName = "fire";
Location targetLocation = player.getLocation();

if (api.hasEffect(effectName)) { // Safety check
    UUID animationId = api.play(effectName, targetLocation, 10.0); // Play it
    
    api.stop(animationId); // Stop it
}
```
