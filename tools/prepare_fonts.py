"""Reproducible small OFL font subsets for the game's symbols and Japanese title."""
from pathlib import Path
import urllib.request
from fontTools.ttLib import TTFont
from fontTools.subset import Subsetter, Options
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
for directory, source, text, name, output in [
    ('notoemoji', 'NotoEmoji%5Bwght%5D.ttf', '✊✋✌ ', 'JankenGestureSymbols', 'gesture_symbols.ttf'),
    ('notosansjp', 'NotoSansJP%5Bwght%5D.ttf', 'じゃんけん', 'JankenJapaneseSubtitle', 'japanese_subtitle.ttf'),
]:
    work = ROOT / '.work/fonts'
    work.mkdir(parents=True, exist_ok=True)
    full = work / output
    urllib.request.urlretrieve(f'https://raw.githubusercontent.com/google/fonts/main/ofl/{directory}/{source}', full)
    font = TTFont(full)
    if 'fvar' in font:
        font = instantiateVariableFont(font, {'wght': 400}, inplace=True)
    subset = Subsetter(options=Options())
    subset.populate(text=text)
    subset.subset(font)
    for entry in font['name'].names:
        if entry.nameID in {1, 3, 4, 6, 16}:
            entry.string = name.encode(entry.getEncoding())
    destination = ROOT / 'shared/src/commonMain/composeResources/font' / output
    destination.parent.mkdir(parents=True, exist_ok=True)
    font.save(destination)
    license_path = ROOT / 'licenses/fonts' / f'{directory}-OFL.txt'
    license_path.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(f'https://raw.githubusercontent.com/google/fonts/main/ofl/{directory}/OFL.txt', license_path)
    print(output, destination.stat().st_size)
