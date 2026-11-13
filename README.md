# MoParticles

MoParticles is a Minecraft **Paper** plugin that parses **MoLang** (the Bedrock
particle language), bakes the result into a Java-edition compatible animation,
generates a resource pack with the necessary textures, and plays the animation
client-side.

**Supported servers:** Paper / Purpur 1.21.5 through **26.3** (and any client
in between can watch at the same time - every packet is encoded per player via
[PacketEvents](https://packetevents.com) 2.14.0). Java 21+.

## How it renders

Effects are played with **purely packet-based, client-side Item Display
entities** - no real entities are ever spawned on the server:

* **Spawn Entity** + **Set Entity Data** when a particle appears
* **Teleport Entity** every tick - the client interpolates the move over the
  display's *position/rotation interpolation duration*, so 20 tps frames look
  perfectly smooth
* **Set Entity Data** only when a particle's scale or tint actually changed
* **Remove Entities** (one batched packet) when particles disappear or a
  viewer leaves

All packets for a viewer are written to their channel and **flushed once per
tick** (`User#writePacket` + `User#flushPackets`), and each viewer's
PacketEvents user is resolved once and cached, so hundreds of particles cost a
single network flush per player per tick.

The exact metadata indexes used are documented in
[`DisplayEntityPackets`](src/main/java/com/csl/moparticles/display/DisplayEntityPackets.java)
and follow the Minecraft wiki protocol documentation
("Java Edition protocol/Entity metadata"), which is identical from 1.21.5 up
to 26.3.

## Resource pack

The generated pack gives every effect an item definition, a plane model and a
texture. The client resolves this chain from the item's
`minecraft:item_model` component:

```
<ns>:particles/<name>                       (item_model component)
  -> assets/<ns>/items/particles/<name>.json        (item definition)
    -> assets/<ns>/models/item/particles/<name>.json (plane model)
      -> assets/<ns>/textures/item/particles/<name>.png
```

Tints travel through the `minecraft:custom_model_data` color list (index 0).
The pack declares format `46` (1.21.4, which introduced item definitions) with
a supported range up to `999`, so one pack serves every supported client.

### Textures

Textures are resolved in this order: the effect's declared texture path, then
`textures/particle/<lastSegment>.png`, then
`textures/particle/<effectName>.png` - first in `plugins/MoParticles/` and
then in the jar. The bundled defaults (`circle.png`, `cube.png`) are **crisp,
hard-edged pixel art** with no anti-aliasing, are refreshed on every startup,
and are tinted at runtime. A crisp circle is generated in code as a final
fallback, and the pack ships a pixel-art `pack.png` icon.

## Build

```
./gradlew shadowJar
```

The final jar is `build/libs/MoParticles-2.0.0-all.jar` (PacketEvents and the
command framework are shaded in - no extra plugins required). Don't forget to
install the generated resource pack (`plugins/MoParticles/generated/resourcepack.zip`).

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

I bundled two effects from snowstorm & a custom made one using snowstorm into
MoParticles for a showcase (in `src/main/resources/particles/`):

* `snowstorm:fire`      - Custom snowstorm fire particle
* `snowstorm:loading`   - Snowstorm's loading circle particle
* `snowstorm:rainbow`   - Snowstorm's rainbow particle animation

Add your own by dropping more `.json` files into
`plugins/MoParticles/particles/` and running `/moparticles reload`.

# Using MoParticles in your plugin

### 1. Build settings
First add the jar to your minecraft plugin.

Gradle
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.Chest-Solutions:MoParticles:2.0.0'
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
        <version>2.0.0</version>
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
