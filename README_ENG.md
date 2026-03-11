# ApertureMapsLib (Minecraft Forge 1.12.2) README

                             .:--.-+**+++=-:.                             
                         -=*#%%=-#%%%%%%%%%%%#*=:                         
                      -*%%%%%#:+%%%%%%%%%%%%%%%%%#+:                      
                   .=#%%%%%%*:#%%%%%%%%%##***+++***#*-                    
                  -#%%%%%%%+:#%%#*+=-:..:=*********+==-.                  
                 :%%%%%%%%*.#+-:. :=++=    :=#%%%%%%%%%%-                 
                *.#%%%%%%#.:-=*=*= *#=:  .=+=-:+#%%%%%%%%=                
               +%=-%%%%%%::*%#:+*- :     *%%%%#- -*%%%%%%%-               
              :%%%:+%%%%=-%%%%%#+=    .-**#%**%%+.=:+%%%%%#               
              +%%%#:+%%#.%%%#*:       .*++---+%%%:=#=:*%%%%-              
              *%%%%#:+%+ +#:..       .#%%%%%%#*%#:.%%#=-#%%=              
              *%%%%%%--=  .:--+=:    .*%%%%%%%#*: .%%%%*:*%=              
              =%%%%%%%*.    .%%%%#=-     :%%%%#:  :%%%%%#:*:              
              .#%%%%%%%#-    :*%%%%=      *%%%-:  +%%%%%%#.               
               :#%%%%%%%%+.    %%#:       =%#-::=.#%%%%%%#.               
                 ===*#%%%%#=.  **.         .  -#-=%%%%%%#:                
                 :##*+======+=:.- .....:::::=#%*.#%%%%%*.                 
                   =#%%%%%%#**++===-==- :=#%%%#.+%%%%#=                   
                    .=#%%%%%%%%%%%#+=-+#%%%%%%-=%%%#=.                    
                       :+#%%%%*+===*%%%%%%%%%-=%#+:                       
                          .:.:+#%%%%%%%%%%%#::-.                          
                                .:-----::.                                

ApertureMapsLib is a coremod-library for Forge 1.12.2 that makes large rectangular (non-square) textures safe and valid to load into the vanilla texture atlas and (optionally) fixes several CTM (ConnectedTexturesMod) edge-cases for pattern textures.

If you create wallpapers/tiles/panels/patterns where a single texture spans many blocks (for example 7x4, 20x12 tiles, etc.), then without special handling Minecraft 1.12.2 may:

- throw an exception during atlas stitching (`TextureAtlasSprite`): `broken aspect ratio and not an animation`;
- “cut” the texture inside the atlas: part of your texture is visually replaced by neighbor sprites;
- produce garbage pixels / seams at edges during CTM rendering because of UV/float precision edge-cases.

This library solves exactly these problems and requires no API calls: enabling happens per-texture via `.png.mcmeta`.

If you build maps using LittleTiles (drawing patterns with the mod), or you make your own non-standard block textures, this library can be useful.

---

## Requirements

- Minecraft: `1.12.2`
- Forge: `14.23.x` (recommended)
- Java: `8`

### CTM (ConnectedTexturesMod)

The library can work **with or without CTM**:

- With CTM: additional UV-math patches are applied for large patterns.
- Without CTM: only atlas/sprite patches apply.

By default CTM is treated as a required dependency (but this can be disabled in the config). I added this option in case you don’t need CTM.

---

## Installation (Client)

1. Put `ApertureMapsLib*.jar` into `mods/`.
2. (Recommended) Put CTM into `mods/`.

The library does not require any calls from your code: it reads `.mcmeta` next to textures and applies patches automatically.

---

## Using it in your mod (for modders)

### Main idea

ApertureMapsLib is enabled selectively: **per specific texture** where you want to allow non-square dimensions and enable the safe atlas mode.

To do that, create a `.png.mcmeta` file next to the PNG and add the `aperturemapslib` section.

### How the library finds `.mcmeta`

Minecraft stores an `iconName` for each sprite in the form:

- `modid:blocks/texture_name`
- `modid:items/texture_name`
- etc.

If `iconName = modid:blocks/example`, the library will look for:

- `assets/modid/textures/blocks/example.png.mcmeta`

Important:
- `.mcmeta` must be included inside your mod jar.
- JSON is read as UTF-8.

---

## `.png.mcmeta` format

Two formats are supported.

### Option 1: minimal (boolean)

The simplest: enable the library with safe defaults.

```json
{
  "aperturemapslib": true
}
```

Equivalent to:
- `enabled=true`
- `square_reserve=true`
- `guard=true`

### Option 2: object with parameters

```json
{
  "aperturemapslib": {
    "enabled": true,
    "square_reserve": true,
    "guard": true
  }
}
```

Notes:
- `enabled` defaults to `true`.
- `square_reserve` defaults to `true`.
  - Alias key `square` is also accepted.
- `guard` defaults to `true`.

### When to disable options

- `square_reserve=false` disables square reservation in the atlas.
  - Saves atlas space, but for large rectangular patterns almost always brings back bugs (cutting/overlap).
- `guard=false` allows the vanilla stitcher to take problematic paths (rotate/scale) for this texture.
  - Usually only for experiments.

If you are not sure, just use `"aperturemapslib": true`.

---

## What the library does (by aspects)

ApertureMapsLib is a coremod and patches several runtime locations.

### 1) Allowing rectangular sprites (TextureAtlasSprite)

Minecraft 1.12.2 contains a vanilla validation check that throws an exception with the message:

- `broken aspect ratio and not an animation`

The library:
- keeps vanilla behavior for normal textures;
- disables this validation **only for managed textures** (where `.mcmeta` contains `aperturemapslib`).

Without this, the vanilla check often crashes resource reload during the atlas build (stitch) stage (or you may get `missingno` if the exception is intercepted somewhere).

### 2) Protection from atlas “cutting” (Stitcher)

Minecraft packs all sprites into one large atlas via `Stitcher`. With certain modpacks/texture sets and atlas sizes, a rectangular sprite may:

- get rotated/scaled;
- or it may end up in a situation where the “free area” next to it is filled by other sprites, so in-game it looks like your sprite is partially replaced by unrelated textures.

ApertureMapsLib fixes this using two mechanisms.

**Square reservation (`square_reserve`)**

- For a managed sprite, the atlas reserves a square `SxS`, where `S = max(width, height)`.
- The real texture occupies only part of that square, the rest remains empty.
- This prevents other sprites from overlapping the area that visually belongs to your pattern.

**Guard (`guard`)**

- For managed sprites, the library disables problematic stitcher paths (rotate/scale) that commonly cause unstable behavior with large textures.

Tradeoff:
- Square reservation uses more VRAM (the atlas may grow). This is the price of correctness.

### 3) CTM patches (if CTM is installed)

For large pattern textures, CTM sometimes runs into float precision and UV boundary issues, which can cause:

- seams,
- garbage pixels,
- incorrect pattern cuts.

The library patches:
- `Submap` interpolation (U/V clamp);
- pattern transform (small clamp + avoids one problematic subdivide path for managed patterns).

Important:
- These CTM patches are applied only to `.mcmeta`-managed textures.

---

## Config (`config/aperturemapslib.cfg`)

The config is auto-generated if missing.

Current template (default):

```properties
# ApertureMapsLib debug config\# Дебаг конфиг ApertureMapsLib
# Core behavior is fixed in code: guard + square reservation are always enabled\# Основная логика защита + square всегда включена в коде
# debug.guard_only=true Unstable guard system (disables square reserve)\# debug.guard_only=true Нестабильная система гвард (отключает square)

# Debug settings\# Дебаг настройки
debug.atlas_dump=true
debug.stitch=false
debug.pattern_samples=false
debug.core.verbose=false
debug.guard_only=false
dependency.ctm.required=true
```

### `dependency.ctm.required`

- `true` (default): CTM is treated as required. If CTM is missing, the library shows an in-game message and explains how to disable the requirement.
- `false`: allow running without CTM.

### Debug flags

- `debug.atlas_dump`
  - dumps the atlas into PNG + a sprite rect list.
- `debug.stitch`
  - logs extended info for managed sprites (sizes, UVs, rotation, origin).
- `debug.pattern_samples`
  - prints a limited number of CTM pattern samples (step/off values).
- `debug.core.verbose`
  - maximum core-level logging.
- `debug.guard_only`
  - disables square reservation and keeps only guard. This mode is intentionally marked as unstable.

---

## Debugging: where to look

### Atlas dump

With `debug.atlas_dump=true`, the library writes:

- folder: `aperturemapslib_debug/`
- files:
  - `atlas_blocks.png`
  - `atlas_blocks_rects.txt`

This is the fastest way to confirm:

- your texture is actually present in the atlas;
- there is a reserved square area around it;
- the sprite is not rotated;
- neighbor textures do not overlap your pattern region.

### Logs

If you are chasing weird glitches, enable one by one:

- `debug.stitch=true`
- `debug.core.verbose=true`
- `debug.pattern_samples=true`

And attach to a report:
- texture dimensions,
- `atlas_blocks.png`,
- a `latest.log` snippet around the stitch stage.

---

## Texture and performance recommendations

Square reservation solves bugs, but it increases atlas usage.

Practical tips:

- Enable `aperturemapslib` only where needed (large rectangular patterns).
- Important: for block patterns, PNG dimensions must be divisible by 16 in both width and height (`N*16 x M*16`), otherwise the texture does not split into `16x16` blocks and the pattern will be incorrect.
- Avoid keeping dozens of huge textures loaded at once.
- Watch your GPU maximum texture size:
  - the atlas may grow to `2048x2048`, `4096x4096` and beyond;
  - if you hit the limit, you will get missing sprites.
- For sane mipmaps, use sizes divisible by 16 (and don’t go too small for the chosen mipmap level).

---

## Troubleshooting (FAQ)

### 1) Still crashes with `broken aspect ratio...`

- The texture is not managed.
- Check the `.mcmeta` path and the `aperturemapslib` section.

### 2) Pattern is cut / replaced by other textures in-game

- Make sure `square_reserve=true`.
- Make sure `debug.guard_only=false`.
- Enable atlas dump and check if the reservation square is visible.

### 3) Says CTM is missing

- Install CTM.
- Or set `dependency.ctm.required=false` in the config.

---

## Building from source

- Requires JDK 8.
- Build:
  - `gradlew build`
- Output:
  - `build/libs/ApertureMapsLib1.12.2-<version>.jar`

The jar is a coremod (manifest contains `FMLCorePlugin`) and must be placed into `mods/`.

---

## Quick steps (no extra fluff)

1. Add your pattern PNG.
2. Add `.png.mcmeta` with `"aperturemapslib": true`.
3. Run with `debug.atlas_dump=true`.
4. Check `atlas_blocks.png`.
5. If everything is fine, disable debug flags before releasing your mod (to avoid extra logs/dumps).

---

If you want to improve the library or you find an unpleasant bug, include in your report:

- texture dimensions,
- `.png.mcmeta`,
- `atlas_blocks.png` and `atlas_blocks_rects.txt`,
- a `latest.log` snippet around stitch.

If something is missing in this README or it is still unclear, feel free to ask directly in the comments and I will reply.

---

This library was created with support from the project: KharkivTiles 1:1

KharkivTiles 1:1 is a project to recreate the city of Kharkiv in Minecraft at 1:1 scale using the LittleTiles mod.

Project Telegram channel:

https://t.me/kharkivstroi

---

If this library helped your project, I would be happy if you credit it in your description or mention the project =)

Special thanks to everyone who follows my project and helps me not to burn out.
                                                   <3

                                           -+***+:    -+***+:                                          
                                         -%@@@@@@@#:=%@@@@@@@*.                                        
                                         %@@@@@@@@@@@@@@@@@@@@=                                        
                                         #@@@@@@@@@@@@@@@@@@@@=                                        
                                         :%@@@@@@@@@@@@@@@@@@*                                         
                                          .*@@@@@@@@@@@@@@@%=                                          
                                            :*@@@@@@@@@@@%+.                                           
                                              :*%@@@@@@%=.                                             
                                                .=%@@#-                                                
                                                   :.                                                  
                                                   =.                                                  
                                                   +.                                                  
                                                   +.                                                  
                                                  .#=                                                  
                                                  -**-:                                                
                                               -:---=%@%*                                              
                                             ::=+++=-####=:                                            
                                          .-:---:-:- ##%%%#+-                                          
                               ..         ::%=*+-+:: ##*+=**#          .:.                             
                              .==         ::-.-=-*=+ #+**#%%%          -.-                             
                         -::---*%%*=.     --@:--::.: #@%#***%       ..:-+=::.                          
                      -::--=-: @%%@@*-    -:@:+*=%=+ #+++=*#@     -:::--.+%##*:.                       
                      = #==+-: #-+==+*::::+:@:::.-:- #@%@%#*%   .--+==*+.+#=-**#+                      
                      = =--*== @-@#%%@%%##****+*-*-+ #@#@#*++++***+*+=@@:+%##%#**                      
                      = =::-:. @-@###%:-:--:=@##-==* #@%@@@@#---.****=@@:+%*=++=*                      
               =+-:.  = #++#=-.@-@*#*#:%+*+-=@**@=+*********+++*:#*-%+@@:+#==**##  .::=+               
            .::==+=#**+ -::=--.@-@*###.+-++==@*+@=%@%#%#%%%%%@@@-#%%@+@@:+%####*%::=*+#@:.             
            =.-==: #*#+ *--=::.@-***++.=:--:=@--@=%@%=#=*=++=*-@-#*-#+**.+#+-++=%  -@%+*#%:            
            = --:: ::-:.*==#+=.@-@%%@#:%+#*+=@%%@=#@@***####%%%@-##+%*@@.+#+=*##@#*#@####%-            
            =:**=+ -=+=.-::::..@-@%@@#.=:--:=@=-@=#@@.###****+:@-##*%+@@.+%####*%+++@**++#-            
           -=::+-=*:+-#:#==*=-.@-@@@@#:#=*+==@*+@=#@@:@@@@@@#+:@-#*-#+@@.+#+-++=%+@@@=+++##*           
           *++++.::.*=%:=--+--.@-@@@@#.+-+=-=@**@=#@%:@*==#@+ .@-#%%%+@@.+#*+###%+#=%*###%@@           
           *+=+=-++-=:=:%*+%++.@-@@@@#.+:=-:=@--@=#@%:@+:.*@+ .@-#*-#+@@.+%#*##*%++ %****#@@           
       ....*---*--=====+@##@##*@#@@@@%++****#@%%@#%@%*@#++#@#=+@*%@@@#@@+#@@@@@@%##-@@@@@@@@:...       
       -========================================================================================                    
