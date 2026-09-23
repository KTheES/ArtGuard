from io import BytesIO
from PIL import Image, ImageDraw
from app.hashes import perceptual_hashes

def fixture():
    image = Image.new("RGB", (128, 128), "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((20, 18, 106, 108), fill="#174a7e")
    draw.ellipse((38, 34, 90, 86), fill="#f4a261")
    draw.line((15, 112, 112, 15), fill="#e63946", width=7)
    return image

def test_hashes_are_stable_under_resize_and_jpeg_compression():
    source = fixture()
    resized = source.resize((256, 256), Image.Resampling.LANCZOS)
    stream = BytesIO(); resized.save(stream, "JPEG", quality=72)
    compressed = Image.open(BytesIO(stream.getvalue())).convert("RGB")
    original = perceptual_hashes(source)
    transformed = perceptual_hashes(compressed)
    distances = [(int(left, 16) ^ int(right, 16)).bit_count() for left, right in zip(original, transformed)]
    print(f"resize+jpeg phash_distance={distances[0]} dhash_distance={distances[1]}")
    assert distances[0] <= 8 and distances[1] <= 8

def test_hashes_are_fixed_width_lowercase_hex():
    assert all(len(value) == 16 and value == value.lower() for value in perceptual_hashes(fixture()))
