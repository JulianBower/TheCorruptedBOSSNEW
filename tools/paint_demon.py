"""Paints little_demon.png / little_demon_eyes.png (64x64) for LittleDemonModel. Needs Pillow."""
import random, sys
from PIL import Image
out = sys.argv[1]
random.seed(7)
def fill(im, x0, y0, w, h, base, var=6):
    px = im.load()
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = random.randint(-var, var)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)
CHAR=(38,36,42); OBS=(24,20,30); CLOAK=(92,12,22); CLOAK_D=(60,8,16); HORN=(20,16,18); TIP=(150,20,24)
im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
fill(im, 0, 0, 24, 12, CHAR)          # head
fill(im, 32, 0, 16, 4, HORN)          # horns
fill(im, 0, 16, 20, 11, OBS)          # body
fill(im, 24, 16, 8, 8, CHAR); fill(im, 40, 16, 8, 8, CHAR)   # arms
fill(im, 0, 32, 12, 8, OBS); fill(im, 12, 32, 12, 8, OBS)    # legs
fill(im, 24, 32, 4, 6, HORN)          # tail
fill(im, 0, 44, 16, 10, CLOAK, 8)     # cloak
px = im.load()
# horn tips glow dull crimson (top face + upper sides)
for x0 in (32, 40):
    for x in range(x0, x0 + 4):
        px[x, 1] = TIP + (255,)
# head front face is (6,6)-(12,12): dark eye sockets and a thin mouth
for (x, y) in [(7,8),(8,8),(10,8),(11,8)]: px[x, y] = (6, 4, 6, 255)
for x in range(8, 11): px[x, 10] = (10, 6, 8, 255)
# tattered cloak: darker folds and ragged bottom edge (cloak front/back faces are y 45..54)
for x in range(0, 16):
    for y in range(44, 54):
        if (x * 3 + y) % 5 == 0: px[x, y] = CLOAK_D + (255,)
for x in range(1, 15, 2):
    for y in range(52 + (x // 2) % 3, 54): px[x, y] = (0, 0, 0, 0)
# belt of crimson trim on the body
for x in range(0, 20): px[x, 22] = CLOAK + (255,)
im.save(out + '/little_demon.png')
eyes = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); ep = eyes.load()
for (x, y) in [(7,8),(8,8),(10,8),(11,8)]: ep[x, y] = (255, 24, 20, 255)
eyes.save(out + '/little_demon_eyes.png')
