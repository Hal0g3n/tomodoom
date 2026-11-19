# Maniengine scene extraction

A Maniengine export (`source/scene-3.html` = the rabbit, `source/scene-5.html` = the cat) is a single HTML file:

| Part | What it is | Kept? |
|---|---|---|
| `m3dLoader(/*O*/[...]/*O*/)` | editor live-edit runtime + a table of manual tweaks (position/rotation/scale/visibility by object name or child index) | table only, as `OVERRIDES` |
| importmap | three.js from the jsDelivr CDN | three.js vendored locally |
| module script | camera, renderer, WASD walk controls, then **the model**: three.js code building named meshes from primitives, procedural textures, vertex edits | model code only |

No mesh data is embedded: the model is code. `extract_model.py` cuts out the model-building code
(from `function m3dSkyDomeTexture` to `__walk.refresh();`) and writes it, with the override
table, to `app/src/main/assets/maniengine/models/<pet>.js` as `buildModel(THREE, scene, renderer)`.
It also downloads the matching three.js build into `assets/maniengine/vendor/three/` (once).
`assets/maniengine/pet-scene.js` loads a pet's module (`loadPetModel`), calls `buildModel`, applies
`OVERRIDES` and builds the add-ons. The app page `assets/maniengine/index.html` uses it (`?pet=<id>`
picks the pet, default `rabbit`) and handles the camera, animation and `tomodoom:petstate`.

```bash
python3 tools/maniengine/extract_model.py                                   # source/scene-3.html → models/rabbit.js
python3 tools/maniengine/extract_model.py source/scene-5.html --pet cat     # → models/cat.js
```

Stdlib only (Python 3.8+). The script fails loudly if an anchor is missing (the export format changed)
or if the extracted code still uses editor-only names (`camera`, `controls`, `__walk`).
`pet-scene.js` looks meshes up by name (head, eyes, body, rug), so each pet needs an entry in its
`PETS` table; a new export with renamed parts needs that entry updated too.

## Item thumbnails

The circles on the Customise page are PNGs of the add-ons in `pet-scene.js`. After changing an
add-on, regenerate them (Python 3.10+, stdlib only, needs Edge or another Chromium browser):

```bash
python3 tools/maniengine/make_thumbnails.py
```

It opens `thumbnails.html` headlessly and writes `app/src/main/res/drawable-nodpi/thumb_<itemId>.png`.
A new catalog item also needs a case in `ui/customise/AccessoryThumbnails.java`.
