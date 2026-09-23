from dataclasses import dataclass
from PIL import Image

REGION_SCHEME = "fixed-overlap-5-v1"

@dataclass(frozen=True)
class Region:
    key: str
    x: float
    y: float
    width: float
    height: float

REGIONS = (
    Region("CENTER", 0.175, 0.175, 0.65, 0.65),
    Region("TOP_LEFT", 0.0, 0.0, 0.65, 0.65),
    Region("TOP_RIGHT", 0.35, 0.0, 0.65, 0.65),
    Region("BOTTOM_LEFT", 0.0, 0.35, 0.65, 0.65),
    Region("BOTTOM_RIGHT", 0.35, 0.35, 0.65, 0.65),
)

def crop_regions(image: Image.Image) -> list[tuple[Region, Image.Image]]:
    width, height = image.size
    result = []
    for region in REGIONS:
        left = round(region.x * width)
        top = round(region.y * height)
        right = min(width, left + round(region.width * width))
        bottom = min(height, top + round(region.height * height))
        result.append((region, image.crop((left, top, right, bottom))))
    return result
